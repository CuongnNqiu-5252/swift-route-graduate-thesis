package com.swiftroute.driverservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

@Builder
public record DriverActivationKeyResponse(
        UUID driverId,
        String driverName,
        String email,
        String activationKey,
        String formattedActivationKey,
        String status,
        Instant expiresAt,
        boolean emailSent) {
}
