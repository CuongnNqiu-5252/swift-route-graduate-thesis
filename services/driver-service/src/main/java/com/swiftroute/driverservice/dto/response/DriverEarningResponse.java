package com.swiftroute.driverservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.swiftroute.driverservice.entity.DriverEarning;
import com.swiftroute.driverservice.entity.enums.EarningStatus;

public record DriverEarningResponse(
        UUID id, UUID orderId, UUID deliveryTaskId, UUID driverId, String driverName,
        String earningType, BigDecimal deliveryFee, BigDecimal payableDistanceKm,
        BigDecimal baseAmount, BigDecimal distanceAmount, BigDecimal bonusAmount,
        BigDecimal adjustmentAmount, BigDecimal netAmount, EarningStatus status,
        UUID settlementId, Instant completedAt) {
    public static DriverEarningResponse from(DriverEarning earning, String driverName) {
        return new DriverEarningResponse(earning.getId(), earning.getOrderId(), earning.getDeliveryTaskId(),
                earning.getDriverId(), driverName, earning.getEarningType(), earning.getDeliveryFee(),
                earning.getPayableDistanceKm(), earning.getBaseAmount(), earning.getDistanceAmount(),
                earning.getBonusAmount(), earning.getAdjustmentAmount(), earning.getNetAmount(),
                earning.getStatus(), earning.getSettlementId(), earning.getCompletedAt());
    }
}
