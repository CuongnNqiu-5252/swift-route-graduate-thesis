package com.swiftroute.orderservice.dto.request;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Moves stops between unassigned draft plans, optionally exchanging two stops.
 * {@code targetIndex} is the zero-based position in the target route after the move.
 */
public record MovePlanStopRequest(
        @JsonProperty("source_plan_id") @NotNull UUID sourcePlanId,
        @JsonProperty("target_plan_id") @NotNull UUID targetPlanId,
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("target_index") @NotNull @PositiveOrZero Integer targetIndex,
        @JsonProperty("expected_source_version") @NotNull @PositiveOrZero Long expectedSourceVersion,
        @JsonProperty("expected_target_version") @NotNull @PositiveOrZero Long expectedTargetVersion,
        @JsonProperty("order_ids") List<@NotNull UUID> orderIds,
        @JsonProperty("swap_order_id") UUID swapOrderId) {

    public MovePlanStopRequest(UUID sourcePlanId, UUID targetPlanId, UUID orderId,
            Integer targetIndex, Long expectedSourceVersion, Long expectedTargetVersion) {
        this(sourcePlanId, targetPlanId, orderId, targetIndex, expectedSourceVersion, expectedTargetVersion, null, null);
    }
}
