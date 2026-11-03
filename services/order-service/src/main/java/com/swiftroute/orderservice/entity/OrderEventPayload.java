package com.swiftroute.orderservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Outbox event payload for order lifecycle events.
 * Published on routing key: order.<event_type>.v1
 * e.g. order.created.v1, order.status_changed.v1, order.ready_for_planning.v1
 *
 * Contains all fields needed by consumers (Routing Engine, Notification).
 */
public record OrderEventPayload(
        UUID eventId,
        String eventType,
        UUID orderId,
        UUID tenantId,
        String externalOrderCode,
        String trackingToken,
        String recipientName,
        String recipientPhone,
        UUID assignedDriverId,   // nullable — set when order is PLANNED
        String previousStatus,
        String currentStatus,
        Double pickupLat,
        Double pickupLng,
        String pickupAddress,
        Double deliveryLat,
        Double deliveryLng,
        String deliveryAddress,
        Double weightKg,
        String priority,         // NORMAL | HIGH | URGENT
        BigDecimal codAmount,
        OffsetDateTime timeWindowStart,
        OffsetDateTime timeWindowEnd,
        Instant occurredAt) {

    public OrderEventPayload(
            UUID eventId,
            String eventType,
            UUID orderId,
            UUID tenantId,
            UUID assignedDriverId,
            String previousStatus,
            String currentStatus,
            Instant occurredAt) {
        this(eventId, eventType, orderId, tenantId, null, null, null, null, assignedDriverId,
                previousStatus, currentStatus, null, null, null, null, null, null, null, "NORMAL", BigDecimal.ZERO, null, null, occurredAt);
    }
}
