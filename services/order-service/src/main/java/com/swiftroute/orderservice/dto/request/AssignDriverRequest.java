package com.swiftroute.orderservice.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AssignDriverRequest(
        @NotNull UUID driverId) {
}
