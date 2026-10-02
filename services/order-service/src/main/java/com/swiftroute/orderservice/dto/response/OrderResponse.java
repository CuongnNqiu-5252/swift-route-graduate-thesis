package com.swiftroute.orderservice.dto.response;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;

/**
 * DTO trả về thông tin đơn hàng cho Frontend.
 * Ánh xạ từ Entity Order, ẩn đi các trường nhạy cảm nội bộ.
 *
 * Dùng static factory method fromEntity() để đóng gói logic mapping.
 */
public record OrderResponse(
        UUID orderId,
        UUID tenantId,
        String tenantName,
        UUID customerId,
        Double pickupLat,
        Double pickupLng,
        Double deliveryLat,
        Double deliveryLng,
        String deliveryAddress,
        LocalTime timeWindowStart,
        LocalTime timeWindowEnd,
        Double weightKg,
        OrderPriority priority,
        OrderStatus status,
        UUID assignedDriverId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    /**
     * Factory method chuyển Entity → DTO.
     * Đặt tại đây (thay vì Service) để logic mapping gần với định nghĩa DTO,
     * dễ thay đổi khi cấu trúc response thay đổi.
     */
    public static OrderResponse fromEntity(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTenant().getId(),
                order.getTenant().getBusinessName(),
                order.getCustomerId(),
                order.getPickupLat(),
                order.getPickupLng(),
                order.getDeliveryLat(),
                order.getDeliveryLng(),
                order.getDeliveryAddress(),
                order.getTimeWindowStart(),
                order.getTimeWindowEnd(),
                order.getWeightKg(),
                order.getPriority(),
                order.getStatus(),
                order.getAssignedDriverId(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
