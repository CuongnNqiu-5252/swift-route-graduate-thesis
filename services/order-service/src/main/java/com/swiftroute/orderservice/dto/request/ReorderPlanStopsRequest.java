package com.swiftroute.orderservice.dto.request;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReorderPlanStopsRequest(
        @JsonProperty("expected_version") @NotNull @PositiveOrZero Long expectedVersion,
        @JsonProperty("ordered_stop_ids") @NotEmpty List<@NotNull UUID> orderedStopIds) {}
