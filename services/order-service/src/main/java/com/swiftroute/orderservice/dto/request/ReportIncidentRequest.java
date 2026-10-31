package com.swiftroute.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReportIncidentRequest(
        @NotBlank(message = "incident_type is required")
        String incidentType,
        String notes
) {}

