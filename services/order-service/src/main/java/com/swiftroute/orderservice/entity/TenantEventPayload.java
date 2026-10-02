package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.util.UUID;

/** Version-independent payload used for tenant lifecycle events. */
public record TenantEventPayload(
        UUID eventId,
        String eventType,
        UUID tenantId,
        UUID ownerUserId,
        Instant occurredAt) {
}
