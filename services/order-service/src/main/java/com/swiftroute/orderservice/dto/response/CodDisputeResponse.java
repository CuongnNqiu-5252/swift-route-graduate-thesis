package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CodDisputeResponse(
        UUID id,
        UUID paymentId,
        UUID handoverId,
        UUID orderId,
        String orderCode,
        UUID driverId,
        String type,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        BigDecimal differenceAmount,
        String status,
        String reason,
        String evidenceUrl,
        String resolutionType,
        BigDecimal adjustmentAmount,
        String resolutionNotes,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant createdAt) {
}
