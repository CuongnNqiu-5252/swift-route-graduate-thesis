package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.orderservice.entity.PackageUnit;

public record PackageUnitResponse(
        UUID packageId,
        UUID orderId,
        String orderCode,
        int packageNumber,
        String packageCode,
        String status,
        UUID currentCustodianId,
        long version,
        Instant updatedAt) {
    public static PackageUnitResponse from(PackageUnit unit) {
        return new PackageUnitResponse(
                unit.getId(),
                unit.getOrder().getId(),
                unit.getOrder().getExternalOrderCode(),
                unit.getPackageNumber(),
                unit.getPackageCode(),
                unit.getStatus(),
                unit.getCurrentCustodianId(),
                unit.getVersion() == null ? 0 : unit.getVersion(),
                unit.getUpdatedAt());
    }
}
