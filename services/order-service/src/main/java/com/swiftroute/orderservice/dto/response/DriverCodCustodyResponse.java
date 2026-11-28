package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DriverCodCustodyResponse(
        UUID driverId,
        BigDecimal availableTotal,
        BigDecimal lockedTotal,
        BigDecimal handedOverTotal,
        BigDecimal openDiscrepancyAmount,
        List<CodCollectionResponse> eligibleCollections,
        List<CodHandoverResponse> recentHandovers) {
}
