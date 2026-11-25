package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PackageCategory;

/**
 * Customer-safe public order tracking projection with PII masking and zero internal UUID exposure.
 */
public record PublicOrderTrackingResponse(
        String trackingToken,
        String orderCode,
        String carrierName,
        OrderStatus status,
        int progressStep,
        String maskedSenderName,
        String maskedPickupArea,
        String maskedRecipientName,
        String maskedRecipientPhone,
        String maskedDeliveryArea,
        PackageCategory packageCategory,
        String packageDescription,
        Double weightKg,
        BigDecimal codAmount,
        String codStatus,
        OffsetDateTime timeWindowStart,
        OffsetDateTime timeWindowEnd,
        String latestProofUrl,
        Instant createdAt,
        Instant updatedAt,
        List<PublicTrackingMilestoneResponse> milestones) {
}
