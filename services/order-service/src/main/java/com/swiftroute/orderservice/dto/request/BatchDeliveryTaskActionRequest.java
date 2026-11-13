package com.swiftroute.orderservice.dto.request;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for batch driver task operations (batch accept route tasks or
 * single-swipe batch pickup at a depot/warehouse).
 */
public record BatchDeliveryTaskActionRequest(
        List<UUID> taskIds,
        List<UUID> orderIds,
        String notes) {
}

