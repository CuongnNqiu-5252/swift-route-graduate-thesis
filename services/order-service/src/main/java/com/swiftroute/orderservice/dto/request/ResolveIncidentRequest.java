package com.swiftroute.orderservice.dto.request;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record ResolveIncidentRequest(
        @NotBlank(message = "resolution_type is required")
        String resolutionType,
        String notes,
        UUID assignedOperatorId,
        Instant rescheduleTime,
        UUID replacementDriverId
) {}

