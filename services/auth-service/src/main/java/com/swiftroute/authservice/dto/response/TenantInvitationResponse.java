package com.swiftroute.authservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.authservice.entity.TenantInvitation;
import com.swiftroute.authservice.entity.enums.TenantInvitationStatus;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;

public record TenantInvitationResponse(
        UUID id,
        UUID tenantId,
        UUID driverId,
        String email,
        TenantMembershipRole role,
        TenantInvitationStatus status,
        UUID invitedBy,
        Instant expiresAt,
        Instant createdAt,
        java.util.List<String> grantedPermissions,
        java.util.List<String> revokedPermissions) {

    public TenantInvitationResponse(
            UUID id,
            UUID tenantId,
            UUID driverId,
            String email,
            TenantMembershipRole role,
            TenantInvitationStatus status,
            UUID invitedBy,
            Instant expiresAt,
            Instant createdAt) {
        this(id, tenantId, driverId, email, role, status, invitedBy, expiresAt, createdAt,
                java.util.List.of(), java.util.List.of());
    }

    public static TenantInvitationResponse fromEntity(TenantInvitation inv) {
        return fromEntity(inv, java.util.List.of(), java.util.List.of());
    }

    public static TenantInvitationResponse fromEntity(
            TenantInvitation inv,
            java.util.List<String> grantedPermissions,
            java.util.List<String> revokedPermissions) {
        return new TenantInvitationResponse(
                inv.getId(),
                inv.getTenantId(),
                inv.getDriverId(),
                inv.getEmail(),
                inv.getRole(),
                inv.getStatus(),
                inv.getInvitedBy(),
                inv.getExpiresAt(),
                inv.getCreatedAt(),
                grantedPermissions != null ? grantedPermissions : java.util.List.of(),
                revokedPermissions != null ? revokedPermissions : java.util.List.of());
    }
}
