package com.swiftroute.driverservice.dto.request;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotEmpty;

public record BulkDriverActivationRequest(
        @JsonAlias({"driverIds", "driver_ids"})
        @NotEmpty(message = "driver_ids must not be empty")
        List<UUID> driverIds,

        @JsonAlias({"sendEmail", "send_email"})
        boolean sendEmail) {
}
