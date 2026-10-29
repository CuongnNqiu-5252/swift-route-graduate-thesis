package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox event payload for order lifecycle events.
 * Published on routing key: order.<event_type>.v1
 * e.g. order.status_changed.v1, order.ready_for_planning.v1
 *
 * Contains only the fields needed by consumers (Routing Engine, Notification).
 * No PII beyond IDs.
 */
public record OrderEventPayload(
        UUID eventId,
        String eventType,
        UUID orderId,
        UUID tenantId,
        UUID customerId,
        UUID assignedDriverId,   // nullable — set when order is PLANNED
        String previousStatus,
        String currentStatus,
        Instant occurredAt) {
}
