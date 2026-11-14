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
        LocalDateTime updatedAt,
        java.util.List<String> grantedPermissions,
        java.util.List<String> revokedPermissions,
        java.util.List<String> effectivePermissions,
        Long permissionsVersion) {

    public TenantMemberResponse(
            UUID membershipId,
            UUID tenantId,
            UUID userId,
            String email,
            String fullName,
            TenantMembershipRole role,
            TenantMembershipStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this(membershipId, tenantId, userId, email, fullName, role, status, createdAt, updatedAt,
                java.util.List.of(), java.util.List.of(),
                com.swiftroute.authservice.entity.enums.TenantPermission.defaultPermissionsForRole(role),
                1L);
    }

    public static TenantMemberResponse fromEntity(TenantMembership tm) {
        return fromEntity(tm,
                java.util.List.of(),
                java.util.List.of(),
                com.swiftroute.authservice.entity.enums.TenantPermission.defaultPermissionsForRole(tm.getMembershipRole()));
    }

    public static TenantMemberResponse fromEntity(
            TenantMembership tm,
            java.util.List<String> grantedPermissions,
            java.util.List<String> revokedPermissions,
            java.util.List<String> effectivePermissions) {
        return new TenantMemberResponse(
                tm.getId(),
                tm.getTenantId(),
                tm.getUser() != null ? tm.getUser().getId() : null,
                tm.getUser() != null ? tm.getUser().getEmail() : null,
                tm.getUser() != null ? tm.getUser().getFullName() : null,
                tm.getMembershipRole(),
                tm.getStatus(),
                tm.getCreatedAt(),
                tm.getUpdatedAt(),
                grantedPermissions != null ? grantedPermissions : java.util.List.of(),
                revokedPermissions != null ? revokedPermissions : java.util.List.of(),
                effectivePermissions != null ? effectivePermissions : java.util.List.of(),
                tm.getPermissionsVersion() != null ? tm.getPermissionsVersion() : 1L);
    }
}

