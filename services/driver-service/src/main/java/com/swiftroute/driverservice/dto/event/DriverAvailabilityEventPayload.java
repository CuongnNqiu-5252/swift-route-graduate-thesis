package com.swiftroute.driverservice.dto.event;

import java.time.Instant;
import java.util.UUID;

public record DriverAvailabilityEventPayload(
        UUID driverId,
        UUID tenantId,
        UUID userId,
        boolean isAvailable,
        Instant occurredAt
) {}
