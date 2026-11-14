package com.swiftroute.orderservice.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignPackageHandoverRequest(
        @NotNull UUID toDriverId,
        @Size(max = 1000) String notes) {
}
