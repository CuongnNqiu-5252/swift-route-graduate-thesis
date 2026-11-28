package com.swiftroute.authservice.event;

import java.time.Instant;
import java.util.UUID;

/** Contract consumed from the swiftroute.events topic exchange. */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public record TenantEvent(
        @com.fasterxml.jackson.annotation.JsonAlias({"event_id", "eventId"}) UUID eventId,
        @com.fasterxml.jackson.annotation.JsonAlias({"event_type", "eventType", "type"}) String eventType,
        @com.fasterxml.jackson.annotation.JsonAlias({"tenant_id", "tenantId"}) UUID tenantId,
        @com.fasterxml.jackson.annotation.JsonAlias({"owner_user_id", "ownerUserId", "user_id", "userId"}) UUID ownerUserId,
        @com.fasterxml.jackson.annotation.JsonAlias({"occurred_at", "occurredAt"}) Instant occurredAt,
        Long version) {

    public TenantEvent(UUID eventId, String eventType, UUID tenantId, UUID ownerUserId, Instant occurredAt) {
        this(eventId, eventType, tenantId, ownerUserId, occurredAt, 1L);
    }
}
