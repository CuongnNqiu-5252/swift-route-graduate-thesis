package com.swiftroute.orderservice.dto.request;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ManualAssignmentRequest(@NotEmpty List<@Valid Assignment> assignments) {
    public record Assignment(@NotNull UUID orderId, @NotNull UUID driverId, UUID planId) {}
}
