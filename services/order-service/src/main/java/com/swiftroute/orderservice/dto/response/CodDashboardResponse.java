package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CodDashboardResponse(
        BigDecimal outstandingFromCustomers,
        BigDecimal inDriverCustody,
        BigDecimal awaitingCashierCount,
        BigDecimal openDiscrepancyAmount,
        long pendingPaymentCount,
        long custodyCollectionCount,
        long pendingHandoverCount,
        long openDisputeCount,
        List<CodHandoverResponse> handovers,
        List<CodDisputeResponse> disputes) {
}
