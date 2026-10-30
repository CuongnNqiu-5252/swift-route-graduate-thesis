package com.swiftroute.orderservice.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for assigning a batch of optimised routes to drivers.
 * Sent by the operations staff after reviewing the routing plan.
 * Supports both explicit assignments list and shorthand (driverId + orderIds).
 */
public record AssignBatchRequest(
        List<AssignmentItem> assignments,
        UUID driverId,
        List<UUID> orderIds) {

    public List<AssignmentItem> getEffectiveAssignments() {
        if (assignments != null && !assignments.isEmpty()) {
            return assignments;
        }
        if (driverId != null && orderIds != null && !orderIds.isEmpty()) {
            return orderIds.stream()
                    .map(oid -> new AssignmentItem(oid, driverId, null))
                    .toList();
        }
        return List.of();
    }

    public record AssignmentItem(
            @NotNull UUID orderId,
            @NotNull UUID driverId,
            UUID planId) {}
}
