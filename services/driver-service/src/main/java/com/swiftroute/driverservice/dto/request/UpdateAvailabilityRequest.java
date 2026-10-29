package com.swiftroute.driverservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateAvailabilityRequest(
        @NotNull(message = "is_available is required")
        Boolean isAvailable
) {}
