package com.swiftroute.driverservice.dto.event;

import java.time.Instant;
import java.util.UUID;

public record DriverDeliveryReportEventPayload(
        UUID driverId,
        UUID tenantId,
        UUID orderId,
        String eventType,
        String reason,
        String notes,
        Instant occurredAt
) {}
