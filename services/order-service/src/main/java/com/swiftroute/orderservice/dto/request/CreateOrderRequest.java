package com.swiftroute.orderservice.dto.request;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.PackageCategory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new delivery order.
 *
 * Identity fields (tenantId, customerId) are injected by the API Gateway
 * via trusted headers (X-Tenant-Id, X-User-Id). They must NOT be accepted
 * from the request body to prevent spoofing.
 *
 * Time windows must be UTC ISO-8601 OffsetDateTime, never plain LocalTime
 * (which has no date or timezone and cannot represent an absolute constraint).
 */
public record CreateOrderRequest(

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")   Double pickupLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double pickupLng,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")   Double deliveryLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double deliveryLng,

        @Size(max = 500) String pickupAddress,
        @Size(max = 500) String deliveryAddress,

        /** UTC start of the allowed delivery window. */
        @NotNull OffsetDateTime timeWindowStart,

        /** UTC end of the allowed delivery window. Must be after timeWindowStart. */
        @NotNull OffsetDateTime timeWindowEnd,

        @NotNull @Positive Double weightKg,

        // Optional — defaults to NORMAL if omitted
        OrderPriority priority,

        // Optional — defaults to PARCEL if omitted
        PackageCategory packageCategory,
        @Size(max = 255) String itemDescription,
        @Size(max = 500) String notes) {
}
