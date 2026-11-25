package com.swiftroute.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.authservice.dto.request.AcceptInvitationRequest;
import com.swiftroute.authservice.dto.request.InviteMemberRequest;
import com.swiftroute.authservice.dto.request.UpdateMemberPermissionsRequest;
import com.swiftroute.authservice.dto.request.UpdateMemberRoleRequest;
import com.swiftroute.authservice.dto.request.UpdateMemberStatusRequest;
import com.swiftroute.authservice.dto.request.UpdateRolePermissionsRequest;
import com.swiftroute.authservice.dto.response.InviteMemberResponse;
import com.swiftroute.authservice.dto.response.PageRequests;
import com.swiftroute.authservice.dto.response.PageResponse;
import com.swiftroute.authservice.dto.response.PermissionCatalogResponse;
import com.swiftroute.authservice.dto.response.TenantInvitationResponse;
import com.swiftroute.authservice.dto.response.TenantMemberResponse;
import com.swiftroute.authservice.entity.TenantInvitation;
import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.Role;
import com.swiftroute.authservice.entity.enums.TenantInvitationStatus;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.entity.enums.TenantPermission;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.repository.RefreshTokenRepository;
import com.swiftroute.authservice.repository.TenantInvitationRepository;
import com.swiftroute.authservice.repository.TenantMembershipRepository;
import com.swiftroute.authservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantEmployeeService {

    private final TenantMembershipRepository tenantMembershipRepository;
    private final TenantInvitationRepository tenantInvitationRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final DriverOnboardingClient driverOnboarding;
    private final VerificationCipher invitationCipher;
    private final TenantDetailsClient tenantDetails;
    @org.springframework.beans.factory.annotation.Value("${verification.web-url:http://localhost:3000}")
    private String invitationWebUrl;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TenantPermissionService tenantPermissionService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public record InvitationPreview(String email, UUID tenantId, String businessName, String role, Instant expiresAt,
            String status) {
    }

    @Transactional
    public InvitationPreview previewInvitation(String token) {
        if (token == null || token.length() != 64)
            throw new AppException(ErrorCode.INVITATION_NOT_FOUND);
        var invitation = tenantInvitationRepository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));
        String status = invitation.getStatus().name();
        if (status.equals("PENDING") && !invitation.getExpiresAt().isAfter(Instant.now()))
            status = "EXPIRED";
        return new InvitationPreview(invitation.getEmail(), invitation.getTenantId(), invitation.getBusinessName(),
                invitation.getRole().name(),
                invitation.getExpiresAt(), status);
    }

    @Transactional
    public InviteMemberResponse inviteMember(UUID tenantId, UUID inviterUserId, InviteMemberRequest request) {
        // Enforce inviter has invite permission and sufficient role hierarchy
        TenantMembership inviterMembership = verifyInviteAccess(tenantId, inviterUserId);
        if (inviterMembership.getMembershipRole() != TenantMembershipRole.OWNER) {
            if (request.role() == TenantMembershipRole.OWNER
                    || TenantPermission.roleRank(request.role()) > TenantPermission
                            .roleRank(inviterMembership.getMembershipRole())) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
        }
        java.util.Set<String> grantedSet = validateCodesOrThrow(request.grantedPermissions());
        java.util.Set<String> revokedSet = validateCodesOrThrow(request.revokedPermissions());
        validateOverridesOrThrow(
                request.role(),
                grantedSet,
                revokedSet,
                inviterMembership.getMembershipRole(),
                new java.util.LinkedHashSet<>(resolveEffectivePermissions(inviterMembership)));

        if (jdbc != null)
            jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) tenantId.hashCode() + ")");

        String email = request.email().trim().toLowerCase();
        String businessName = tenantDetails.businessName(tenantId);
        if (request.driverId() != null) {
            if (request.role() != TenantMembershipRole.DRIVER)
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            driverOnboarding.validate(tenantId, request.driverId());
        }

        // Prevent inviting existing active member
        Optional<TenantMembership> existingActive = tenantMembershipRepository
                .findByTenantIdAndUser_EmailIgnoreCase(tenantId, email)
                .filter(m -> m.getStatus() == TenantMembershipStatus.ACTIVE);
        if (existingActive.isPresent()) {
            throw new AppException(ErrorCode.TENANT_MEMBERSHIP_ALREADY_EXISTS);
        }

        // Revoke any existing PENDING invitations for this email in this tenant
        List<TenantInvitation> priorInvitations = tenantInvitationRepository.findByTenantIdAndEmailIgnoreCase(tenantId,
                email);
        for (TenantInvitation prior : priorInvitations) {
            if (prior.getStatus() == TenantInvitationStatus.PENDING) {
                prior.setStatus(TenantInvitationStatus.REVOKED);
                tenantInvitationRepository.save(prior);
            }
        }

        // Generate cryptographically secure token & hash
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);
        String tokenHash = hashToken(rawToken);

        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        String grantedJson = serializePermissionSet(grantedSet);
        String revokedJson = serializePermissionSet(revokedSet);

        TenantInvitation invitation = TenantInvitation.builder()
                .tenantId(tenantId)
                .driverId(request.driverId())
                .businessName(businessName)
                .email(email)
                .role(request.role())
                .tokenHash(tokenHash)
                .status(TenantInvitationStatus.PENDING)
                .invitedBy(inviterUserId)
                .grantedPermissions(grantedJson)
                .revokedPermissions(revokedJson)
                .expiresAt(expiresAt)
                .build();

        TenantInvitation saved = tenantInvitationRepository.save(invitation);
        String invitationLink = invitationWebUrl + "/invitations?token=" + rawToken;
        if (jdbc != null) {
            jdbc.update(
                    "INSERT INTO auth_notification_outbox(event_id,user_id,email,encrypted_body,event_type) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), inviterUserId, email,
                    invitationCipher.encrypt(businessName + "\n" + invitationLink), "tenant.driver_invited.v1");
        }
        log.info("Created tenant invitation id={} for email={} to tenantId={}", saved.getId(), email, tenantId);

        return new InviteMemberResponse(
                saved.getId(),
                saved.getTenantId(),
                saved.getDriverId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getStatus(),
                rawToken, // Sent to caller once
                saved.getExpiresAt(),
                saved.getCreatedAt(),
                List.copyOf(grantedSet),
                List.copyOf(revokedSet));
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantMemberResponse> memberPage(UUID tenantId, UUID requesterId, int page, int size) {
        verifyMemberAccess(tenantId, requesterId);
        return PageResponse.fromPage(tenantMembershipRepository.findByTenantId(tenantId,
                PageRequests.newestFirst(page, size, "createdAt")).map(this::toMemberResponse));
    }

    @Transactional(readOnly = true)
    public List<TenantMemberResponse> getTenantMembers(UUID tenantId, UUID requesterId) {
        verifyMemberAccess(tenantId, requesterId);
        List<TenantMembership> members = tenantMembershipRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return members.stream().map(this::toMemberResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantInvitationResponse> invitationPage(UUID tenantId, UUID requesterId, int page, int size) {
        verifyAdminAccess(tenantId, requesterId);
        return PageResponse.fromPage(tenantInvitationRepository.findByTenantId(tenantId,
                PageRequests.newestFirst(page, size, "createdAt")).map(this::toInvitationResponse));
    }

    @Transactional(readOnly = true)
    public List<TenantInvitationResponse> getTenantInvitations(UUID tenantId, UUID requesterId) {
        verifyAdminAccess(tenantId, requesterId);
        List<TenantInvitation> invitations = tenantInvitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return invitations.stream().map(this::toInvitationResponse).toList();
    }

    @Transactional
    public TenantMemberResponse updateMemberStatus(UUID tenantId, UUID membershipId, UpdateMemberStatusRequest request,
            UUID requesterId) {
        TenantMembership requesterMembership = verifyAdminAccess(tenantId, requesterId);

        if (jdbc != null)
            jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) tenantId.hashCode() + ")");
        TenantMembership membership = tenantMembershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_MEMBERSHIP_NOT_FOUND));

        TenantMembershipStatus targetStatus = request.status();

        if (requesterMembership.getMembershipRole() == TenantMembershipRole.TENANT_ADMIN) {
            if (membership.getMembershipRole() == TenantMembershipRole.OWNER) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            if (membership.getUser().getId().equals(requesterId)
                    && targetStatus != TenantMembershipStatus.ACTIVE) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
        }

        // Safety Guard: Cannot suspend or deactivate the last remaining active OWNER
        if (membership.getMembershipRole() == TenantMembershipRole.OWNER
                && targetStatus != TenantMembershipStatus.ACTIVE
                && membership.getStatus() == TenantMembershipStatus.ACTIVE) {

            long activeOwnerCount = tenantMembershipRepository
                    .countByTenantIdAndMembershipRoleAndStatus(tenantId, TenantMembershipRole.OWNER,
                            TenantMembershipStatus.ACTIVE);
            if (activeOwnerCount <= 1) {
                log.warn("Attempt to suspend/deactivate the last active OWNER membershipId={} in tenantId={}",
                        membershipId, tenantId);
                throw new AppException(ErrorCode.CANNOT_SUSPEND_LAST_OWNER);
            }
        }

        membership.setStatus(targetStatus);
        TenantMembership saved = tenantMembershipRepository.save(membership);

        if (targetStatus != TenantMembershipStatus.ACTIVE) {
            User user = membership.getUser();
            refreshTokenRepository.revokeAllByUserId(user.getId());
            user.setStatus(targetStatus.name());
            userRepository.save(user);
        } else {
            User user = membership.getUser();
            if (!"ACTIVE".equals(user.getStatus())) {
                user.setStatus("ACTIVE");
                userRepository.save(user);
            }
        }

        log.info("Updated membership id={} in tenantId={} to status={}", saved.getId(), tenantId, targetStatus);
        return toMemberResponse(saved);
    }

    @Transactional(readOnly = true)
    public PermissionCatalogResponse getPermissionsCatalog(UUID tenantId, UUID requesterId) {
        verifyMemberAccess(tenantId, requesterId);
        if (tenantPermissionService != null) {
            return tenantPermissionService.getPermissionsCatalog(tenantId);
        }
        java.util.List<PermissionCatalogResponse.PermissionItemDTO> items = java.util.Arrays
                .stream(TenantPermission.values())
                .map(p -> new PermissionCatalogResponse.PermissionItemDTO(
                        p.getCode(), p.getModule(), p.getDisplayName(), p.getDescription(), p.isDriverOnly()))
                .toList();
        java.util.Map<String, List<String>> defaults = new java.util.LinkedHashMap<>();
        for (TenantMembershipRole r : TenantMembershipRole.values()) {
            defaults.put(r.name(), TenantPermission.defaultPermissionsForRole(r));
        }
        return new PermissionCatalogResponse(items, defaults, defaults);
    }

    @Transactional
    public java.util.Map<String, List<String>> updateRolePermissionsTemplate(
            UUID tenantId,
            TenantMembershipRole targetRole,
            UpdateRolePermissionsRequest request,
            UUID requesterId) {
        TenantMembership requester = verifyManageMemberAccess(tenantId, requesterId);
        if (requester.getMembershipRole() != TenantMembershipRole.OWNER) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "Only the enterprise OWNER can customize role permission templates");
        }
        if (targetRole == null || targetRole == TenantMembershipRole.OWNER) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "OWNER role template cannot be modified");
        }
        if (jdbc != null)
            jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) tenantId.hashCode() + ")");

        if (tenantPermissionService == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        tenantPermissionService.updateRoleTemplate(tenantId, targetRole, request.permissions(), requesterId);

        // Bump permissions_version for existing members of this role
        List<TenantMembership> affected = tenantMembershipRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .filter(m -> m.getMembershipRole() == targetRole)
                .toList();
        for (TenantMembership member : affected) {
            long currentVer = member.getPermissionsVersion() != null ? member.getPermissionsVersion() : 1L;
            member.setPermissionsVersion(currentVer + 1L);
            TenantMembership savedMember = tenantMembershipRepository.save(member);
            if (savedMember.getUser() != null) {
                refreshTokenRepository.revokeAllByUserId(savedMember.getUser().getId());
            }
            enqueuePermissionsSyncOutbox(savedMember);
        }
        return tenantPermissionService.getTenantRoleTemplates(tenantId);
    }

    @Transactional
    public TenantMemberResponse updateMemberRole(
            UUID tenantId,
            UUID membershipId,
            UpdateMemberRoleRequest request,
            UUID requesterId) {
        TenantMembership requesterMembership = verifyManageMemberAccess(tenantId, requesterId);
        if (jdbc != null)
            jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) tenantId.hashCode() + ")");

        TenantMembership membership = tenantMembershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_MEMBERSHIP_NOT_FOUND));

        if (membership.getUser() != null && membership.getUser().getId().equals(requesterId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Cannot modify your own role");
        }

        TenantMembershipRole targetRole = request.role();
        TenantMembershipRole actorRole = requesterMembership.getMembershipRole();

        // Only OWNER can modify an OWNER or assign OWNER / equal-or-higher rank
        if (actorRole != TenantMembershipRole.OWNER) {
            if (membership.getMembershipRole() == TenantMembershipRole.OWNER
                    || targetRole == TenantMembershipRole.OWNER
                    || TenantPermission.roleRank(actorRole) <= TenantPermission.roleRank(membership.getMembershipRole())
                    || TenantPermission.roleRank(actorRole) <= TenantPermission.roleRank(targetRole)) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
        }

        // Cannot demote the last active OWNER
        if (membership.getMembershipRole() == TenantMembershipRole.OWNER
                && targetRole != TenantMembershipRole.OWNER
                && membership.getStatus() == TenantMembershipStatus.ACTIVE) {
            long activeOwnerCount = tenantMembershipRepository
                    .countByTenantIdAndMembershipRoleAndStatus(tenantId, TenantMembershipRole.OWNER,
                            TenantMembershipStatus.ACTIVE);
            if (activeOwnerCount <= 1) {
                throw new AppException(ErrorCode.CANNOT_SUSPEND_LAST_OWNER);
            }
        }

        if (targetRole == TenantMembershipRole.OWNER) {
            membership.setGrantedPermissions("[]");
            membership.setRevokedPermissions("[]");
        } else if (targetRole != TenantMembershipRole.DRIVER) {
            // Strip any driverOnly permissions from grantedPermissions when moving away
            // from DRIVER
            List<String> filteredGranted = parsePermissionCodes(membership.getGrantedPermissions()).stream()
                    .filter(code -> TenantPermission.fromCode(code).map(p -> !p.isDriverOnly()).orElse(false))
                    .toList();
            membership.setGrantedPermissions(serializePermissionSet(new java.util.LinkedHashSet<>(filteredGranted)));
        }

        membership.setMembershipRole(targetRole);
        long currentVer = membership.getPermissionsVersion() != null ? membership.getPermissionsVersion() : 1L;
        membership.setPermissionsVersion(currentVer + 1L);
        TenantMembership saved = tenantMembershipRepository.save(membership);

        if (membership.getUser() != null) {
            refreshTokenRepository.revokeAllByUserId(membership.getUser().getId());
        }
        enqueuePermissionsSyncOutbox(saved);

        log.info("Updated membership id={} in tenantId={} to role={}", saved.getId(), tenantId, targetRole);
        return toMemberResponse(saved);
    }

    @Transactional
    public TenantMemberResponse updateMemberPermissions(
            UUID tenantId,
            UUID membershipId,
            UpdateMemberPermissionsRequest request,
            UUID requesterId) {
        TenantMembership requesterMembership = verifyManageMemberAccess(tenantId, requesterId);
        if (jdbc != null)
            jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) tenantId.hashCode() + ")");

        TenantMembership membership = tenantMembershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_MEMBERSHIP_NOT_FOUND));

        if (membership.getUser() != null && membership.getUser().getId().equals(requesterId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Cannot modify your own permissions");
        }

        if (membership.getMembershipRole() == TenantMembershipRole.OWNER) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Cannot modify permissions of an OWNER");
        }

        TenantMembershipRole actorRole = requesterMembership.getMembershipRole();
        if (actorRole != TenantMembershipRole.OWNER
                && TenantPermission.roleRank(actorRole) <= TenantPermission.roleRank(membership.getMembershipRole())) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "Cannot modify permissions of a member with equal or higher role rank");
        }

        java.util.Set<String> grantedSet = validateCodesOrThrow(request.grantedPermissions());
        java.util.Set<String> revokedSet = validateCodesOrThrow(request.revokedPermissions());
        validateOverridesOrThrow(
                membership.getMembershipRole(),
                grantedSet,
                revokedSet,
                actorRole,
                new java.util.LinkedHashSet<>(resolveEffectivePermissions(requesterMembership)));

        membership.setGrantedPermissions(serializePermissionSet(grantedSet));
        membership.setRevokedPermissions(serializePermissionSet(revokedSet));
        long currentVer = membership.getPermissionsVersion() != null ? membership.getPermissionsVersion() : 1L;
        membership.setPermissionsVersion(currentVer + 1L);

        TenantMembership saved = tenantMembershipRepository.save(membership);
        if (membership.getUser() != null) {
            refreshTokenRepository.revokeAllByUserId(membership.getUser().getId());
        }
        enqueuePermissionsSyncOutbox(saved);

        log.info("Updated granular permissions for membership id={} in tenantId={} (ver={})",
                saved.getId(), tenantId, saved.getPermissionsVersion());
        return toMemberResponse(saved);
    }

    @Transactional
    public TenantMemberResponse acceptInvitation(AcceptInvitationRequest request) {
        String tokenHash = hashToken(request.token().trim());
        TenantInvitation invitation = tenantInvitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));

        if (invitation.getStatus() != TenantInvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_ALREADY_PROCESSED);
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(TenantInvitationStatus.EXPIRED);
            tenantInvitationRepository.save(invitation);
            throw new AppException(ErrorCode.INVITATION_EXPIRED);
        }

        String email = invitation.getEmail();
        User user = userRepository.findByEmail(email).orElse(null);

        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());

        if (isAuthenticated) {
            // Authenticated browser flow: user must match the invitation account
            if (user == null || !user.getId().toString().equals(authentication.getName())) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            if (request.fullName() != null && !request.fullName().isBlank()) {
                user.setFullName(request.fullName().trim());
            }
            user = userRepository.save(user);
        } else {
            // Direct / mobile activation flow: raw token proves possession of the invited
            // link
            if (user == null) {
                if (request.password() == null || request.password().length() < 6) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR);
                }
                user = User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .fullName(
                                request.fullName() != null && !request.fullName().isBlank() ? request.fullName().trim()
                                        : "Member")
                        .role(Role.USER)
                        .emailVerified(true)
                        .status("ACTIVE")
                        .build();
                user = userRepository.save(user);
                log.info("Created new user id={} from accepted invitation", user.getId());
            } else {
                if (request.password() != null && request.password().length() >= 6) {
                    user.setPasswordHash(passwordEncoder.encode(request.password()));
                }
                if (request.fullName() != null && !request.fullName().isBlank()) {
                    user.setFullName(request.fullName().trim());
                }
                user.setEmailVerified(true);
                user.setStatus("ACTIVE");
                user = userRepository.save(user);
                log.info("Activated existing user id={} from accepted invitation", user.getId());
            }
        }

        final User finalUser = user;
        if (invitation.getRole() == TenantMembershipRole.DRIVER) {
            boolean otherBusiness = tenantMembershipRepository
                    .findByUser_IdAndStatus(user.getId(), TenantMembershipStatus.ACTIVE)
                    .stream().anyMatch(m -> m.getMembershipRole() == TenantMembershipRole.DRIVER
                            && !m.getTenantId().equals(invitation.getTenantId()));
            if (otherBusiness)
                throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        TenantMembership membership = tenantMembershipRepository
                .findByTenantIdAndUser_Id(invitation.getTenantId(), user.getId())
                .orElseGet(() -> TenantMembership.builder()
                        .tenantId(invitation.getTenantId())
                        .user(finalUser)
                        .membershipRole(invitation.getRole())
                        .status(TenantMembershipStatus.ACTIVE)
                        .lastEventVersion(0L)
                        .build());

        if (membership.getId() != null && membership.getMembershipRole() != invitation.getRole()) {
            throw new AppException(ErrorCode.TENANT_MEMBERSHIP_ALREADY_EXISTS);
        }
        membership.setMembershipRole(invitation.getRole());
        membership.setStatus(TenantMembershipStatus.ACTIVE);
        membership.setGrantedPermissions(
                invitation.getGrantedPermissions() != null ? invitation.getGrantedPermissions() : "[]");
        membership.setRevokedPermissions(
                invitation.getRevokedPermissions() != null ? invitation.getRevokedPermissions() : "[]");
        if (membership.getPermissionsVersion() == null) {
            membership.setPermissionsVersion(1L);
        }
        TenantMembership savedMembership = tenantMembershipRepository.save(membership);

        if (invitation.getDriverId() != null) {
            driverOnboarding.link(invitation.getTenantId(), invitation.getDriverId(), user.getId());
        } else if (invitation.getRole() == TenantMembershipRole.DRIVER) {
            driverOnboarding.provision(invitation.getTenantId(), user.getId(), user.getFullName(), user.getPhone());
        }

        invitation.setStatus(TenantInvitationStatus.ACCEPTED);
        tenantInvitationRepository.save(invitation);

        log.info("Accepted invitation id={} for user id={} into tenantId={}", invitation.getId(), finalUser.getId(),
                invitation.getTenantId());
        return toMemberResponse(savedMembership);
    }

    @Transactional
    public void revokeInvitation(UUID tenantId, UUID invitationId, UUID requesterId) {
        verifyInviteAccess(tenantId, requesterId);
        if (jdbc != null)
            jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) tenantId.hashCode() + ")");
        var invitation = tenantInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.getTenantId().equals(tenantId))
            throw new AppException(ErrorCode.ACCESS_DENIED);
        if (invitation.getStatus() != TenantInvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_ALREADY_PROCESSED);
        }
        invitation.setStatus(TenantInvitationStatus.REVOKED);
        tenantInvitationRepository.save(invitation);
    }

    private TenantMembership verifyAdminAccess(UUID tenantId, UUID userId) {
        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));

        if (membership.getStatus() != TenantMembershipStatus.ACTIVE) {
            throw new AppException(ErrorCode.TENANT_MEMBERSHIP_SUSPENDED);
        }

        if (membership.getMembershipRole() == TenantMembershipRole.OWNER) {
            return membership;
        }
        List<String> effective = resolveEffectivePermissions(membership);
        if (membership.getMembershipRole() == TenantMembershipRole.TENANT_ADMIN
                || effective.contains(TenantPermission.MEMBER_MANAGE.getCode())
                || effective.contains(TenantPermission.MEMBER_INVITE.getCode())) {
            return membership;
        }
        throw new AppException(ErrorCode.ACCESS_DENIED);
    }

    private TenantMembership verifyInviteAccess(UUID tenantId, UUID userId) {
        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));

        if (membership.getStatus() != TenantMembershipStatus.ACTIVE) {
            throw new AppException(ErrorCode.TENANT_MEMBERSHIP_SUSPENDED);
        }

        if (membership.getMembershipRole() == TenantMembershipRole.OWNER) {
            return membership;
        }
        List<String> effective = resolveEffectivePermissions(membership);
        if (!effective.contains(TenantPermission.MEMBER_INVITE.getCode())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return membership;
    }

    private TenantMembership verifyManageMemberAccess(UUID tenantId, UUID userId) {
        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));

        if (membership.getStatus() != TenantMembershipStatus.ACTIVE) {
            throw new AppException(ErrorCode.TENANT_MEMBERSHIP_SUSPENDED);
        }

        if (membership.getMembershipRole() == TenantMembershipRole.OWNER) {
            return membership;
        }
        List<String> effective = resolveEffectivePermissions(membership);
        if (!effective.contains(TenantPermission.MEMBER_MANAGE.getCode())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return membership;
    }

    private void verifyMemberAccess(UUID tenantId, UUID userId) {
        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));

        if (membership.getStatus() != TenantMembershipStatus.ACTIVE) {
            throw new AppException(ErrorCode.TENANT_MEMBERSHIP_SUSPENDED);
        }
    }

    private List<String> resolveEffectivePermissions(TenantMembership membership) {
        if (tenantPermissionService != null) {
            return tenantPermissionService.resolveEffectivePermissions(membership);
        }
        List<String> template = TenantPermission.defaultPermissionsForRole(membership.getMembershipRole());
        List<String> granted = parsePermissionCodes(membership.getGrantedPermissions());
        List<String> revoked = parsePermissionCodes(membership.getRevokedPermissions());
        java.util.Set<String> effective = new java.util.LinkedHashSet<>(template);
        effective.addAll(granted);
        effective.removeAll(revoked);
        return List.copyOf(effective);
    }

    private List<String> parsePermissionCodes(String json) {
        if (tenantPermissionService != null) {
            return tenantPermissionService.parsePermissionJson(json);
        }
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<String> raw = mapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                    });
            return List.copyOf(TenantPermission.normalizeAndValidateCodes(raw));
        } catch (Exception e) {
            return List.of();
        }
    }

    private java.util.Set<String> validateCodesOrThrow(List<String> rawCodes) {
        if (tenantPermissionService != null) {
            return tenantPermissionService.validatePermissionCodes(rawCodes);
        }
        try {
            return TenantPermission.normalizeAndValidateCodes(rawCodes);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private void validateOverridesOrThrow(
            TenantMembershipRole targetRole,
            java.util.Set<String> granted,
            java.util.Set<String> revoked,
            TenantMembershipRole actorRole,
            java.util.Set<String> actorEffectivePermissions) {
        if (tenantPermissionService != null) {
            tenantPermissionService.validateMemberOverrides(
                    targetRole, granted, revoked, actorRole, actorEffectivePermissions, false);
            return;
        }
        if (targetRole == TenantMembershipRole.OWNER && (!granted.isEmpty() || !revoked.isEmpty())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cannot customize OWNER permissions");
        }
        for (String code : granted) {
            if (revoked.contains(code)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Permission cannot be both granted and revoked: " + code);
            }
            TenantPermission perm = TenantPermission.fromCode(code)
                    .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Unknown permission: " + code));
            if (perm.isDriverOnly() && targetRole != TenantMembershipRole.DRIVER) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Permission '" + code + "' is restricted to DRIVER role only");
            }
        }
        if (actorRole != TenantMembershipRole.OWNER) {
            for (String code : granted) {
                if (!actorEffectivePermissions.contains(code)) {
                    throw new AppException(ErrorCode.ACCESS_DENIED,
                            "Cannot grant permission '" + code + "' that you do not hold yourself");
                }
            }
            for (String code : revoked) {
                if (!actorEffectivePermissions.contains(code)) {
                    throw new AppException(ErrorCode.ACCESS_DENIED,
                            "Cannot revoke permission '" + code + "' outside your own permission scope");
                }
            }
        }
    }

    private String serializePermissionSet(java.util.Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "[]";
        }
        if (tenantPermissionService != null) {
            return tenantPermissionService.toPermissionJson(new java.util.ArrayList<>(codes));
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(new java.util.ArrayList<>(codes));
        } catch (Exception e) {
            return "[]";
        }
    }

    private TenantMemberResponse toMemberResponse(TenantMembership tm) {
        List<String> granted = parsePermissionCodes(tm.getGrantedPermissions());
        List<String> revoked = parsePermissionCodes(tm.getRevokedPermissions());
        List<String> effective = resolveEffectivePermissions(tm);
        return TenantMemberResponse.fromEntity(tm, granted, revoked, effective);
    }

    private TenantInvitationResponse toInvitationResponse(TenantInvitation inv) {
        List<String> granted = parsePermissionCodes(inv.getGrantedPermissions());
        List<String> revoked = parsePermissionCodes(inv.getRevokedPermissions());
        return TenantInvitationResponse.fromEntity(inv, granted, revoked);
    }

    private void enqueuePermissionsSyncOutbox(TenantMembership membership) {
        if (jdbc == null || invitationCipher == null || membership == null || membership.getUser() == null) {
            return;
        }
        try {
            java.util.Map<String, Object> syncPayload = new java.util.LinkedHashMap<>();
            syncPayload.put("tenantId", membership.getTenantId() != null ? membership.getTenantId().toString() : "");
            syncPayload.put("userId", membership.getUser().getId().toString());
            syncPayload.put("membershipRole", membership.getMembershipRole().name());
            syncPayload.put("status", membership.getStatus().name());
            syncPayload.put("permissions", resolveEffectivePermissions(membership));
            syncPayload.put("permissionsVersion",
                    membership.getPermissionsVersion() != null ? membership.getPermissionsVersion() : 1L);
            String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(syncPayload);
            jdbc.update(
                    "INSERT INTO auth_notification_outbox(event_id,user_id,email,encrypted_body,event_type) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    membership.getUser().getId(),
                    membership.getUser().getEmail() != null ? membership.getUser().getEmail() : "",
                    invitationCipher.encrypt(jsonPayload),
                    "tenant.member_permissions_updated.v1");
        } catch (Exception ex) {
            log.warn("Failed to enqueue permissions sync outbox for membership {}: {}",
                    membership.getId(), ex.getMessage());
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
