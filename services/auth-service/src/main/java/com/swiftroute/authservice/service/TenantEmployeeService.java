package com.swiftroute.authservice.service;

import com.swiftroute.authservice.dto.response.PageRequests;
import com.swiftroute.authservice.dto.response.PageResponse;
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
import com.swiftroute.authservice.dto.request.UpdateMemberStatusRequest;
import com.swiftroute.authservice.dto.response.InviteMemberResponse;
import com.swiftroute.authservice.dto.response.TenantInvitationResponse;
import com.swiftroute.authservice.dto.response.TenantMemberResponse;
import com.swiftroute.authservice.entity.TenantInvitation;
import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.Role;
import com.swiftroute.authservice.entity.enums.TenantInvitationStatus;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
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
        // Enforce inviter has OWNER or ADMIN role
        TenantMembership inviterMembership = verifyAdminAccess(tenantId, inviterUserId);
        if (inviterMembership.getMembershipRole() == TenantMembershipRole.TENANT_ADMIN
                && request.role() == TenantMembershipRole.OWNER) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
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

        TenantInvitation invitation = TenantInvitation.builder()
                .tenantId(tenantId)
                .driverId(request.driverId())
                .businessName(businessName)
                .email(email)
                .role(request.role())
                .tokenHash(tokenHash)
                .status(TenantInvitationStatus.PENDING)
                .invitedBy(inviterUserId)
                .expiresAt(expiresAt)
                .build();

        TenantInvitation saved = tenantInvitationRepository.save(invitation);
        String invitationLink = invitationWebUrl + "/invitations?token=" + rawToken;
        if (request.role() == TenantMembershipRole.DRIVER) {
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
                saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantMemberResponse> memberPage(UUID tenantId, UUID requesterId, int page, int size) {
        verifyMemberAccess(tenantId, requesterId);
        return PageResponse.fromPage(tenantMembershipRepository.findByTenantId(tenantId,
                PageRequests.newestFirst(page, size, "createdAt")).map(TenantMemberResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public List<TenantMemberResponse> getTenantMembers(UUID tenantId, UUID requesterId) {
        verifyMemberAccess(tenantId, requesterId);
        List<TenantMembership> members = tenantMembershipRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return members.stream().map(TenantMemberResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantInvitationResponse> invitationPage(UUID tenantId, UUID requesterId, int page, int size) {
        verifyAdminAccess(tenantId, requesterId);
        return PageResponse.fromPage(tenantInvitationRepository.findByTenantId(tenantId,
                PageRequests.newestFirst(page, size, "createdAt")).map(TenantInvitationResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public List<TenantInvitationResponse> getTenantInvitations(UUID tenantId, UUID requesterId) {
        verifyAdminAccess(tenantId, requesterId);
        List<TenantInvitation> invitations = tenantInvitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return invitations.stream().map(TenantInvitationResponse::fromEntity).toList();
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
        return TenantMemberResponse.fromEntity(saved);
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
        return TenantMemberResponse.fromEntity(savedMembership);
    }

    @Transactional
    public void revokeInvitation(UUID tenantId, UUID invitationId, UUID requesterId) {
        verifyAdminAccess(tenantId, requesterId);
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

        if (membership.getMembershipRole() != TenantMembershipRole.OWNER
                && membership.getMembershipRole() != TenantMembershipRole.TENANT_ADMIN) {
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
