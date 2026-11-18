package com.swiftroute.orderservice.dto.request;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Moves one stop from an unassigned draft plan into another unassigned draft plan.
 * {@code targetIndex} is the zero-based position in the target route after the move.
 */
public record MovePlanStopRequest(
        @JsonProperty("source_plan_id") @NotNull UUID sourcePlanId,
        @JsonProperty("target_plan_id") @NotNull UUID targetPlanId,
        @JsonProperty("order_id") @NotNull UUID orderId,
        @JsonProperty("target_index") @NotNull @PositiveOrZero Integer targetIndex,
        @JsonProperty("expected_source_version") @NotNull @PositiveOrZero Long expectedSourceVersion,
        @JsonProperty("expected_target_version") @NotNull @PositiveOrZero Long expectedTargetVersion) {}
