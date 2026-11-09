package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CodHandoverResponse(
        UUID id,
        UUID driverId,
        String status,
        BigDecimal ledgerTotal,
        BigDecimal declaredTotal,
        BigDecimal countedTotal,
        BigDecimal discrepancyAmount,
        String notes,
        Instant submittedAt,
        UUID verifiedBy,
        Instant verifiedAt,
        List<Line> lines) {

    public record Line(
            UUID collectionId,
            UUID orderId,
            String orderCode,
            BigDecimal amount,
            String outcome) {
    }
}
