package com.swiftroute.authservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.authservice.entity.enums.TenantInvitationStatus;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;

public record InviteMemberResponse(
        UUID id,
        UUID tenantId,
        String email,
        TenantMembershipRole role,
        TenantInvitationStatus status,
        String inviteToken,
        Instant expiresAt,
        Instant createdAt) {
}

