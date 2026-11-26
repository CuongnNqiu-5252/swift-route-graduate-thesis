package com.swiftroute.authservice.dto.response;

import java.util.UUID;

import lombok.Builder;

@Builder
public record DriverActivationAuthResponseDTO(
        String accessToken,
        String refreshToken,
        UUID userId,
        UUID driverId,
        UUID tenantId,
        UserProfileResponseDTO user) {
}
