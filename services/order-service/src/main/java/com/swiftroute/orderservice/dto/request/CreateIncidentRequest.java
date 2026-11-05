package com.swiftroute.orderservice.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record CreateIncidentRequest(
        UUID orderId,
        UUID dispatchPlanId,
        @NotBlank(message = "incident_type is required")
        String incidentType,
        String severity,
        String notes,
        List<String> evidenceUrls,
        Boolean canContinueDelivery,
        Boolean needsImmediateAssistance,
        Integer contactAttempts
) {
    public CreateIncidentRequest {
        if (severity == null || severity.isBlank()) {
            severity = "MEDIUM";
        }
        if (canContinueDelivery == null) {
            canContinueDelivery = true;
        }
        if (needsImmediateAssistance == null) {
            needsImmediateAssistance = false;
        }
        if (contactAttempts == null) {
            contactAttempts = 1;
        }
    }
}
