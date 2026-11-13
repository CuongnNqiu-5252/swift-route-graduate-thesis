package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PackageCategory;

/**
 * DTO trả về thông tin vận đơn giao hàng (Delivery Waybill) cho Frontend/Client.
 */
public record OrderResponse(
        UUID orderId,
        UUID tenantId,
        String tenantName,
        String externalOrderCode,
        String trackingToken,
        String senderName,
        String senderPhone,
        String recipientName,
        String recipientPhone,
        UUID pickupLocationId,
        String pickupLocationName,
        Double pickupLat,
        Double pickupLng,
        String pickupAddress,
        Double deliveryLat,
        Double deliveryLng,
        String deliveryAddress,
        OffsetDateTime timeWindowStart,
        OffsetDateTime timeWindowEnd,
        Double weightKg,
        Integer packageCount,
        OrderPriority priority,
        OrderStatus status,
        PackageCategory packageCategory,
        String packageDescription,
        String notes,
        BigDecimal codAmount,
        BigDecimal declaredValue,
        BigDecimal deliveryFee,
        String payer,
        BigDecimal totalAmount,
        UUID assignedDriverId,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse fromEntity(Order order) {
        if (order == null) return null;
        return new OrderResponse(
                order.getId(),
                order.getTenant() != null ? order.getTenant().getId() : null,
                order.getTenant() != null ? order.getTenant().getBusinessName() : null,
                order.getExternalOrderCode(),
                order.getTrackingToken(),
                order.getSenderName(),
                order.getSenderPhone(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getPickupLocation() != null ? order.getPickupLocation().getId() : null,
                order.getPickupLocation() != null ? order.getPickupLocation().getName() : null,
                order.getPickupLat(),
                order.getPickupLng(),
                order.getPickupAddress(),
                order.getDeliveryLat(),
                order.getDeliveryLng(),
                order.getDeliveryAddress(),
                order.getTimeWindowStart(),
                order.getTimeWindowEnd(),
                order.getWeightKg(),
                order.getPackageCount(),
                order.getPriority(),
                order.getStatus(),
                order.getPackageCategory(),
                order.getPackageDescription(),
                order.getNotes(),
                order.getCodAmount(),
                order.getDeclaredValue(),
                order.getDeliveryFee(),
                order.getPayer(),
                order.getTotalAmount(),
                order.getAssignedDriverId(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
