package com.swiftroute.driverservice.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
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
