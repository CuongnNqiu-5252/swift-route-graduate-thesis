package com.swiftroute.authservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.authservice.entity.TenantInvitation;
import com.swiftroute.authservice.entity.enums.TenantInvitationStatus;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;

public record TenantInvitationResponse(
        UUID id,
        UUID tenantId,
        String email,
        TenantMembershipRole role,
        TenantInvitationStatus status,
        UUID invitedBy,
        Instant expiresAt,
        Instant createdAt) {

    public static TenantInvitationResponse fromEntity(TenantInvitation inv) {
        return new TenantInvitationResponse(
                inv.getId(),
                inv.getTenantId(),
                inv.getEmail(),
                inv.getRole(),
                inv.getStatus(),
                inv.getInvitedBy(),
                inv.getExpiresAt(),
                inv.getCreatedAt());
    }
}

