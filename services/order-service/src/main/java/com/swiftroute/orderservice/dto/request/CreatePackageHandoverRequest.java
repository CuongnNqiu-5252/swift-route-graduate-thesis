package com.swiftroute.orderservice.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePackageHandoverRequest(
        @NotNull UUID toDriverId,
        @NotEmpty @Size(max = 100) List<UUID> packageIds,
        @NotBlank @Size(max = 1000) String reason) {
}
