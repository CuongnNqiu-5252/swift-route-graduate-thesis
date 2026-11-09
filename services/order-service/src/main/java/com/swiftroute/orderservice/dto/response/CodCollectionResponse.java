package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CodCollectionResponse(
        UUID id,
        UUID paymentId,
        UUID orderId,
        String orderCode,
        UUID driverId,
        BigDecimal expectedAmount,
        BigDecimal amount,
        String outcome,
        String status,
        String reason,
        String evidenceUrl,
        Instant occurredAt,
        Instant receivedAt) {
}
