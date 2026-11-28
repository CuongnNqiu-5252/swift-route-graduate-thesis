package com.swiftroute.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for cancelling a dispatch plan.
 */
public record CancelPlanRequest(
        @JsonProperty("reason")
        @NotBlank
        String reason
) {}

