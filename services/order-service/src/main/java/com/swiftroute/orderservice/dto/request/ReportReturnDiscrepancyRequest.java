package com.swiftroute.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record ReportReturnDiscrepancyRequest(
        @NotBlank(message = "discrepancy_type is required")
        String discrepancyType,
        @Min(0) @Max(1) Integer actualQuantity,
        String discrepancyNotes,
        String evidenceUrl
) {}

