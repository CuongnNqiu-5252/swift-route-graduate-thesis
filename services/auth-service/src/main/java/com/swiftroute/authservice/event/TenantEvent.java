package com.swiftroute.authservice.event;

import java.time.Instant;
import java.util.UUID;

/** Contract consumed from the swiftroute.events topic exchange. */
public record TenantEvent(
        UUID eventId,
        String eventType,
        UUID tenantId,
        UUID ownerUserId,
        Instant occurredAt) {
}
