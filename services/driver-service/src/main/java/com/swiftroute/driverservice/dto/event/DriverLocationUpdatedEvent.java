package com.swiftroute.driverservice.dto.event;

import java.time.Instant;
import java.util.UUID;

public record DriverLocationUpdatedEvent(
        UUID eventId,
        UUID driverId,
        UUID userId,
        UUID tenantId,
        double lat,
        double lng,
        Double accuracyM,
        Double speedMps,
        Double bearing,
        Instant recordedAt,
        long sequence) {
}
