package com.swiftroute.orderservice.dto.request;

import java.time.OffsetDateTime;

import com.swiftroute.orderservice.entity.enums.OrderPriority;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO for partial updates to an order (PATCH /api/orders/{orderId})
 * while the order is still in READY_FOR_DISPATCH or PLANNED state.
 * Only non-null fields are applied.
 */
public record UpdateOrderRequest(
        @Size(max = 100) String externalOrderCode,
        @Size(max = 100) String recipientName,
        @Size(max = 50) String recipientPhone,
        OffsetDateTime timeWindowStart,
        OffsetDateTime timeWindowEnd,
        @Positive(message = "Khối lượng phải lớn hơn 0") Double weightKg,
        @Size(max = 500) String packageDescription,
        @Size(max = 500) String notes,
        OrderPriority priority
) {}
