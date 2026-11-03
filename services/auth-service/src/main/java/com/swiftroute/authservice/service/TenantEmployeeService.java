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
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public InviteMemberResponse inviteMember(UUID tenantId, UUID inviterUserId, InviteMemberRequest request) {
        // Enforce inviter has OWNER or ADMIN role
        verifyAdminAccess(tenantId, inviterUserId);

        String email = request.email().trim().toLowerCase();

        // Prevent inviting existing active member
        Optional<TenantMembership> existingActive = tenantMembershipRepository
                .findByTenantIdAndUser_EmailIgnoreCase(tenantId, email)
                .filter(m -> m.getStatus() == TenantMembershipStatus.ACTIVE);
        if (existingActive.isPresent()) {
            throw new AppException(ErrorCode.TENANT_MEMBERSHIP_ALREADY_EXISTS);
        }

        // Revoke any existing PENDING invitations for this email in this tenant
        List<TenantInvitation> priorInvitations = tenantInvitationRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email);
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
                .email(email)
                .role(request.role())
                .tokenHash(tokenHash)
                .status(TenantInvitationStatus.PENDING)
                .invitedBy(inviterUserId)
                .expiresAt(expiresAt)
                .build();

        TenantInvitation saved = tenantInvitationRepository.save(invitation);
        log.info("Created tenant invitation id={} for email={} to tenantId={}", saved.getId(), email, tenantId);

        return new InviteMemberResponse(
                saved.getId(),
                saved.getTenantId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getStatus(),
                rawToken, // Sent to caller once
                saved.getExpiresAt(),
                saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<TenantMemberResponse> getTenantMembers(UUID tenantId, UUID requesterId) {
        verifyMemberAccess(tenantId, requesterId);
        List<TenantMembership> members = tenantMembershipRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return members.stream().map(TenantMemberResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<TenantInvitationResponse> getTenantInvitations(UUID tenantId, UUID requesterId) {
        verifyAdminAccess(tenantId, requesterId);
        List<TenantInvitation> invitations = tenantInvitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return invitations.stream().map(TenantInvitationResponse::fromEntity).toList();
    }

    @Transactional
    public TenantMemberResponse updateMemberStatus(UUID tenantId, UUID membershipId, UpdateMemberStatusRequest request, UUID requesterId) {
        verifyAdminAccess(tenantId, requesterId);

        if (jdbc != null) jdbc.execute("SELECT pg_advisory_xact_lock(" + (long)tenantId.hashCode() + ")");
        TenantMembership membership = tenantMembershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_MEMBERSHIP_NOT_FOUND));

        TenantMembershipStatus targetStatus = request.status();

        // Safety Guard: Cannot suspend or deactivate the last remaining active OWNER
        if (membership.getMembershipRole() == TenantMembershipRole.OWNER
                && targetStatus != TenantMembershipStatus.ACTIVE
                && membership.getStatus() == TenantMembershipStatus.ACTIVE) {

            long activeOwnerCount = tenantMembershipRepository
                    .countByTenantIdAndMembershipRoleAndStatus(tenantId, TenantMembershipRole.OWNER, TenantMembershipStatus.ACTIVE);
            if (activeOwnerCount <= 1) {
                log.warn("Attempt to suspend/deactivate the last active OWNER membershipId={} in tenantId={}", membershipId, tenantId);
                throw new AppException(ErrorCode.CANNOT_SUSPEND_LAST_OWNER);
            }
        }

        membership.setStatus(targetStatus);
        TenantMembership saved = tenantMembershipRepository.save(membership);
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
        Role invitedRole = mapUserRole(invitation.getRole());

        if (user == null) {
            if (request.password() == null || request.password().length() < 6) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
            user = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(request.password()))
                    .fullName(request.fullName() != null && !request.fullName().isBlank() ? request.fullName().trim() : "Member")
                    .role(Role.CUSTOMER)
                    .emailVerified(true)
                    .build();
            user = userRepository.save(user);
            log.info("Created new user id={} from accepted invitation", user.getId());
        } else {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !user.getId().toString().equals(authentication.getName())) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            if (request.fullName() != null && !request.fullName().isBlank()) {
                user.setFullName(request.fullName().trim());
            }
            user = userRepository.save(user);
        }

        final User finalUser = user;
        TenantMembership membership = tenantMembershipRepository
                .findByTenantIdAndUser_Id(invitation.getTenantId(), user.getId())
                .orElseGet(() -> TenantMembership.builder()
                        .tenantId(invitation.getTenantId())
                        .user(finalUser)
                        .membershipRole(invitation.getRole())
                        .status(TenantMembershipStatus.ACTIVE)
                        .lastEventVersion(0L)
                        .build());

        membership.setMembershipRole(invitation.getRole());
        membership.setStatus(TenantMembershipStatus.ACTIVE);
        TenantMembership savedMembership = tenantMembershipRepository.save(membership);

        invitation.setStatus(TenantInvitationStatus.ACCEPTED);
        tenantInvitationRepository.save(invitation);

        log.info("Accepted invitation id={} for user id={} into tenantId={}", invitation.getId(), finalUser.getId(), invitation.getTenantId());
        return TenantMemberResponse.fromEntity(savedMembership);
    }

    private Role mapUserRole(TenantMembershipRole membershipRole) {
        return switch (membershipRole) {
            case OWNER -> Role.OWNER;
            case ADMIN -> Role.TENANT_ADMIN;
            case OPERATOR -> Role.OPS_STAFF;
            case DRIVER -> Role.DRIVER;
            case VIEWER -> Role.PARTNER;
        };
    }

    private void verifyAdminAccess(UUID tenantId, UUID userId) {
        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));

        if (membership.getStatus() != TenantMembershipStatus.ACTIVE) {
            throw new AppException(ErrorCode.TENANT_MEMBERSHIP_SUSPENDED);
        }

        if (membership.getMembershipRole() != TenantMembershipRole.OWNER
                && membership.getMembershipRole() != TenantMembershipRole.ADMIN) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
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
