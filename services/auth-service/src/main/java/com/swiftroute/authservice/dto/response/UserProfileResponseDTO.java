package com.swiftroute.authservice.dto.response;

import com.swiftroute.authservice.entity.enums.Role;
import com.swiftroute.authservice.entity.enums.TenantPermission;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User profile response DTO for GET /api/auth/me.
 */
public record UserProfileResponseDTO(
        UUID id,
        String email,
        String phone,
        String fullName,
        Role accountRole,
        String role,
        UUID tenantId,
        String status,
        LocalDateTime createdAt,
        boolean emailVerified,
        List<String> permissions) {

    public UserProfileResponseDTO(
            UUID id,
            String email,
            String phone,
            String fullName,
            Role accountRole,
            String role,
            UUID tenantId,
            String status,
            LocalDateTime createdAt,
            boolean emailVerified) {
        this(id, email, phone, fullName, accountRole, role, tenantId, status, createdAt, emailVerified,
                TenantPermission.defaultPermissionsForRoleName(role, accountRole));
    }
}
