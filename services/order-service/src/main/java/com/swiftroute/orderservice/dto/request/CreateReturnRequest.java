package com.swiftroute.orderservice.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReturnRequest(
        @NotNull(message = "order_id is required")
        UUID orderId,
        UUID incidentId,
        @NotBlank(message = "reason is required")
        String reason,
        UUID returnLocationId,
        String notes,
        String evidenceUrl
) {}
