package com.swiftroute.orderservice.dto.response;

import com.swiftroute.orderservice.entity.enums.DeliveryZoneEnforcementMode;

public record DeliveryZoneCheckResponse(
        boolean configured,
        boolean allowed,
        boolean inZone,
        double distanceKm,
        Double radiusKm,
        DeliveryZoneEnforcementMode enforcementMode,
        String message
) {}
