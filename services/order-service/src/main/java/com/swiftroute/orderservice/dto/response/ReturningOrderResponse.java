package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.enums.OrderStatus;

/**
 * Lightweight projection for failed/returning/returned orders in the Operations Center.
 */
public record ReturningOrderResponse(
        UUID orderId,
        String orderCode,
        OrderStatus status,
        UUID assignedDriverId,
        String recipientName,
        String recipientPhone,
        String deliveryAddress,
        String pickupLocationName,
        String pickupAddress,
        BigDecimal codAmount,
        String latestReason,
        Instant updatedAt) {

    public static ReturningOrderResponse from(Order order, String latestReason) {
        return new ReturningOrderResponse(
                order.getId(),
                order.getExternalOrderCode(),
                order.getStatus(),
                order.getAssignedDriverId(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getDeliveryAddress(),
                order.getPickupLocation() != null ? order.getPickupLocation().getName() : null,
                order.getPickupAddress(),
                order.getCodAmount(),
                latestReason,
                order.getUpdatedAt());
    }
}
