package com.swiftroute.orderservice.dto.request;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Request body for the manual plan approval endpoint.
 */
public record ApprovePlanRequest(
        @JsonProperty("driver_id")
        UUID driverId,

        @JsonProperty("notes")
        @Size(max = 500)
        String notes
) {}
