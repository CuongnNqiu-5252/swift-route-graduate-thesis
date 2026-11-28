package com.swiftroute.driverservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

@Builder
public record DriverActivationStatusResponse(
        UUID driverId,
        String driverCode,
        UUID userId,
        String fullName,
        String phone,
        String email,
        String licensePlate,
        String vehicleType,
        String operationalStatus,
        String profileStatus,
        String activationStatus,
        Instant keyCreatedAt,
        Instant keyExpiresAt,
        String appVersion,
        String deviceOs,
        String osVersion,
        String deviceModel,
        Instant activatedAt,
        Instant lastSeenAt,
        Instant deactivatedAt) {
}
