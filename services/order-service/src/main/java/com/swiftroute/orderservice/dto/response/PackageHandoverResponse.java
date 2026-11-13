package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftroute.orderservice.entity.PackageHandover;

public record PackageHandoverResponse(
        UUID handoverId,
        UUID fromDriverId,
        UUID toDriverId,
        String status,
        String reason,
        String notes,
        Instant createdAt,
        List<PackageUnitResponse> packages) {
    public static PackageHandoverResponse from(PackageHandover handover) {
        return new PackageHandoverResponse(
                handover.getId(),
                handover.getFromDriverId(),
                handover.getToDriverId(),
                handover.getStatus(),
                handover.getReason(),
                handover.getNotes(),
                handover.getCreatedAt(),
                handover.getItems().stream().map(item -> PackageUnitResponse.from(item.getPackageUnit())).toList());
    }
}
