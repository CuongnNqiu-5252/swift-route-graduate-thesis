package com.swiftroute.authservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.swiftroute.authservice.entity.enums.Role;

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
                LocalDateTime createdAt, boolean emailVerified) {
}
