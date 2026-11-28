package com.swiftroute.authservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.authservice.entity.enums.TenantInvitationStatus;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;

public record InviteMemberResponse(
        UUID id,
        UUID tenantId,
        UUID driverId,
        String email,
        TenantMembershipRole role,
        TenantInvitationStatus status,
        String inviteToken,
        Instant expiresAt,
        Instant createdAt,
        java.util.List<String> grantedPermissions,
        java.util.List<String> revokedPermissions) {

    public InviteMemberResponse(
            UUID id,
            UUID tenantId,
            UUID driverId,
            String email,
            TenantMembershipRole role,
            TenantInvitationStatus status,
            String inviteToken,
            Instant expiresAt,
            Instant createdAt) {
        this(id, tenantId, driverId, email, role, status, inviteToken, expiresAt, createdAt,
                java.util.List.of(), java.util.List.of());
    }
}
