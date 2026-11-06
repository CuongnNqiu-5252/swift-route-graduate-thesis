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
        Instant completedAt) {}
