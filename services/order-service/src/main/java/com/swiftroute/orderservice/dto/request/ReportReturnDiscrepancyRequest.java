package com.swiftroute.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReportReturnDiscrepancyRequest(
        @NotBlank(message = "discrepancy_type is required")
        String discrepancyType,
        String discrepancyNotes,
        String evidenceUrl
) {}

