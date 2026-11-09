package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CodPaymentSummaryResponse(
        UUID paymentId,
        UUID orderId,
        String orderCode,
        BigDecimal expectedAmount,
        BigDecimal collectedTotal,
        BigDecimal remainingAmount,
        String status,
        String currency,
        List<CodCollectionResponse> collections) {
}
