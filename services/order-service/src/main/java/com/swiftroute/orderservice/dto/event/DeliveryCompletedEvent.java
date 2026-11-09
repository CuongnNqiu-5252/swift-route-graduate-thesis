package com.swiftroute.orderservice.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeliveryCompletedEvent(
        UUID eventId,
        UUID orderId,
        UUID deliveryTaskId,
        UUID tenantId,
        UUID assignedDriverId,
        Double pickupLat,
        Double pickupLng,
        Double deliveryLat,
        Double deliveryLng,
        Double weightKg,
        BigDecimal deliveryFee,
        Instant completedAt,
        String orderCode,
        String recipientName,
        String deliveryAddress,
        BigDecimal codAmount) {

    public DeliveryCompletedEvent(
            UUID eventId,
            UUID orderId,
            UUID deliveryTaskId,
            UUID tenantId,
            UUID assignedDriverId,
            Double pickupLat,
            Double pickupLng,
            Double deliveryLat,
            Double deliveryLng,
            Double weightKg,
            BigDecimal deliveryFee,
            Instant completedAt) {
        this(
                eventId,
                orderId,
                deliveryTaskId,
                tenantId,
                assignedDriverId,
                pickupLat,
                pickupLng,
                deliveryLat,
                deliveryLng,
                weightKg,
                deliveryFee,
                completedAt,
                null,
                null,
                null,
                null);
    }
}
