package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftroute.orderservice.entity.enums.OrderStatus;

/**
 * Unified order activity timeline response aggregating operational milestones and evidence.
 */
public record OrderTimelineResponse(
        UUID orderId,
        String orderCode,
        String trackingToken,
        OrderStatus currentStatus,
        UUID assignedDriverId,
        UUID custodianDriverId,
        UUID dispatchPlanId,
        String latestProofUrl,
        Instant createdAt,
        Instant updatedAt,
        List<OrderTimelineEventResponse> events) {
}
