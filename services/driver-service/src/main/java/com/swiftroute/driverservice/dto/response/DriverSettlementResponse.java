package com.swiftroute.driverservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.swiftroute.driverservice.entity.DriverSettlement;
import com.swiftroute.driverservice.entity.enums.SettlementStatus;

public record DriverSettlementResponse(UUID id, UUID driverId, String driverName, Instant periodStart,
        Instant periodEnd, SettlementStatus status, int earningCount, BigDecimal totalAmount,
        UUID approvedBy, Instant approvedAt, UUID paidBy, Instant paidAt, String paymentReference,
        Instant createdAt) {
    public static DriverSettlementResponse from(DriverSettlement settlement, String driverName) {
        return new DriverSettlementResponse(settlement.getId(), settlement.getDriverId(), driverName,
                settlement.getPeriodStart(), settlement.getPeriodEnd(), settlement.getStatus(),
                settlement.getEarningCount(), settlement.getTotalAmount(), settlement.getApprovedBy(),
                settlement.getApprovedAt(), settlement.getPaidBy(), settlement.getPaidAt(),
                settlement.getPaymentReference(), settlement.getCreatedAt());
    }
}
