package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.orderservice.entity.TenantDeliveryZone;
import com.swiftroute.orderservice.entity.enums.DeliveryZoneEnforcementMode;
import com.swiftroute.orderservice.entity.enums.DeliveryZoneType;

public record DeliveryZoneResponse(
        boolean configured,
        UUID zoneId,
        UUID tenantId,
        DeliveryZoneType zoneType,
        Double centerLat,
        Double centerLng,
        Double radiusKm,
        DeliveryZoneEnforcementMode enforcementMode,
        Boolean isActive,
        Instant updatedAt
) {
    public static DeliveryZoneResponse unconfigured(UUID tenantId) {
        return new DeliveryZoneResponse(
                false,
                null,
                tenantId,
                DeliveryZoneType.CIRCLE,
                null,
                null,
                null,
                DeliveryZoneEnforcementMode.STRICT_BLOCK,
                false,
                null
        );
    }

    public static DeliveryZoneResponse fromEntity(TenantDeliveryZone zone) {
        return new DeliveryZoneResponse(
                true,
                zone.getId(),
                zone.getTenant().getId(),
                zone.getZoneType(),
                zone.getCenterLat(),
                zone.getCenterLng(),
                zone.getRadiusKm(),
                zone.getEnforcementMode(),
                zone.getIsActive(),
                zone.getUpdatedAt()
        );
    }
}
