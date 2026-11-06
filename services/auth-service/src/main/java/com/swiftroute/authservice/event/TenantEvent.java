package com.swiftroute.authservice.event;

import java.time.Instant;
import java.util.UUID;

/** Contract consumed from the swiftroute.events topic exchange. */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public record TenantEvent(
        UUID eventId,
        String eventType,
        UUID tenantId,
        UUID ownerUserId,
        Instant occurredAt,
        Long version) {

    public TenantEvent(UUID eventId, String eventType, UUID tenantId, UUID ownerUserId, Instant occurredAt) {
        this(eventId, eventType, tenantId, ownerUserId, occurredAt, 1L);
    }
}
