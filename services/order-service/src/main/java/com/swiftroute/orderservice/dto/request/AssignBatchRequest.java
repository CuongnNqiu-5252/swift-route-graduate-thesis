package com.swiftroute.orderservice.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for assigning a batch of optimised routes to drivers.
 * Sent by the operations staff after reviewing the routing plan.
 */
public record AssignBatchRequest(
        @NotEmpty List<AssignmentItem> assignments) {

    public record AssignmentItem(
            @NotNull UUID orderId,
            @NotNull UUID driverId,
            UUID planId) {}
}
