package com.swiftroute.driverservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record EarningSummaryResponse(
        Instant periodStart,
        Instant periodEnd,
        BigDecimal pendingAmount,
        BigDecimal approvedAmount,
        BigDecimal paidAmount,
        BigDecimal totalAmount,
        int deliveryCount,
        String currency) {}
