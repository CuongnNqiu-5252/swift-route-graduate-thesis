package com.swiftroute.orderservice.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record CreateIncidentRequest(
        UUID orderId,
        UUID dispatchPlanId,
        @NotBlank(message = "incident_type is required") String incidentType,
        String notes,
        List<String> evidenceUrls) {
}
