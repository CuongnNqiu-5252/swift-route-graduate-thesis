package com.swiftroute.driverservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpdateLocationRequest(
        @NotNull(message = "lat is required")
        @DecimalMin(value = "-90.0", message = "lat must be >= -90.0")
        @DecimalMax(value = "90.0", message = "lat must be <= 90.0")
        Double lat,

        @NotNull(message = "lng is required")
        @DecimalMin(value = "-180.0", message = "lng must be >= -180.0")
        @DecimalMax(value = "180.0", message = "lng must be <= 180.0")
        Double lng
) {}
