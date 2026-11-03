package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.orderservice.entity.PickupLocation;
import com.swiftroute.orderservice.entity.enums.PickupLocationStatus;

public record PickupLocationResponse(
        UUID id,
        UUID tenantId,
        String name,
        String address,
        Double lat,
        Double lng,
        String contactName,
        String contactPhone,
        Boolean isDefault,
        PickupLocationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static PickupLocationResponse fromEntity(PickupLocation entity) {
        return new PickupLocationResponse(
                entity.getId(),
                entity.getTenant() != null ? entity.getTenant().getId() : null,
                entity.getName(),
                entity.getAddress(),
                entity.getLat(),
                entity.getLng(),
                entity.getContactName(),
                entity.getContactPhone(),
                entity.getIsDefault(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

