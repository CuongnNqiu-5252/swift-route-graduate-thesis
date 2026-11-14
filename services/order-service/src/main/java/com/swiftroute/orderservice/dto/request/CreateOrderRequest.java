package com.swiftroute.orderservice.dto.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.PackageCategory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new delivery waybill (Vận đơn giao hàng).
 *
 * For SaaS Delivery Operations, no product catalog SKU or customer account is required.
 * An order directly captures sender, recipient, parcel specs, time window, and COD amount.
 */
public record CreateOrderRequest(

        // External reference
        @Size(max = 100) String externalOrderCode,

        // Sender information (Optional: defaults to pickupLocation contact or tenant)
        @Size(max = 100) String senderName,
        @Size(max = 50) String senderPhone,

        // Recipient information (Required for physical parcel delivery)
        @NotBlank(message = "Tên người nhận không được để trống")
        @Size(max = 100) String recipientName,

        @NotBlank(message = "Số điện thoại người nhận không được để trống")
        @Size(max = 50) String recipientPhone,

        // Pickup point
        UUID pickupLocationId,
        Double pickupLat,
        Double pickupLng,
        @Size(max = 500) String pickupAddress,

        // Delivery point
        @NotNull(message = "Vĩ độ điểm giao không được để trống")
        @DecimalMin("-90.0") @DecimalMax("90.0") Double deliveryLat,
        @NotNull(message = "Kinh độ điểm giao không được để trống")
        @DecimalMin("-180.0") @DecimalMax("180.0") Double deliveryLng,
        @NotBlank(message = "Địa chỉ điểm giao không được để trống")
        @Size(max = 500) String deliveryAddress,

        // Allowed delivery window (UTC ISO-8601 OffsetDateTime)
        @NotNull(message = "Khung giờ bắt đầu giao không được để trống")
        OffsetDateTime timeWindowStart,
        @NotNull(message = "Khung giờ kết thúc giao không được để trống")
        OffsetDateTime timeWindowEnd,

        // Parcel specs
        @NotNull(message = "Khối lượng bưu kiện không được để trống")
        @Positive(message = "Khối lượng phải lớn hơn 0") Double weightKg,
        PackageCategory packageCategory,
        @Size(max = 500) String packageDescription,
        @Size(max = 500) String notes,
        OrderPriority priority,

        // Financial & COD fields
        @DecimalMin("0.0") BigDecimal codAmount,
        @DecimalMin("0.0") BigDecimal declaredValue,
        @DecimalMin("0.0") BigDecimal deliveryFee,
        String payer
) {
    public CreateOrderRequest {
        if (priority == null) {
            priority = OrderPriority.NORMAL;
        }
        if (packageCategory == null) {
            packageCategory = PackageCategory.PARCEL;
        }
        if (codAmount == null) {
            codAmount = BigDecimal.ZERO;
        }
        if (declaredValue == null) {
            declaredValue = BigDecimal.ZERO;
        }
        if (deliveryFee == null) {
            deliveryFee = BigDecimal.ZERO;
        }
        if (payer == null || payer.isBlank()) {
            payer = "SENDER";
        }
    }
}
