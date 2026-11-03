package com.swiftroute.authservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;

public record TenantMemberResponse(
        UUID membershipId,
        UUID tenantId,
        UUID userId,
        String email,
        String fullName,
        TenantMembershipRole role,
        TenantMembershipStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TenantMemberResponse fromEntity(TenantMembership tm) {
        return new TenantMemberResponse(
                tm.getId(),
                tm.getTenantId(),
                tm.getUser() != null ? tm.getUser().getId() : null,
                tm.getUser() != null ? tm.getUser().getEmail() : null,
                tm.getUser() != null ? tm.getUser().getFullName() : null,
                tm.getMembershipRole(),
                tm.getStatus(),
                tm.getCreatedAt(),
                tm.getUpdatedAt());
    }
}

