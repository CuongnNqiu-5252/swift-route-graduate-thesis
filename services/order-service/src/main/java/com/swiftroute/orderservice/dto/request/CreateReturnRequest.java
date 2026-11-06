package com.swiftroute.orderservice.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReturnRequest(
        @NotNull(message = "order_id is required")
        UUID orderId,
        UUID incidentId,
        String requestType,
        @NotBlank(message = "reason is required")
        String reason,
        UUID returnLocationId,
        Integer packageCount,
        String notes,
        String evidenceUrl
) {
    public CreateReturnRequest {
        if (requestType == null || requestType.isBlank()) {
            requestType = "RETURN_TO_ORIGIN";
        }
        if (packageCount == null || packageCount <= 0) {
            packageCount = 1;
        }
    }
}
