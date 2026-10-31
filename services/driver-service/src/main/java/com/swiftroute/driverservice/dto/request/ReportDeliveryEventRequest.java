package com.swiftroute.driverservice.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportDeliveryEventRequest(
        @NotNull(message = "order_id is required")
        UUID orderId,

        @NotBlank(message = "event_type is required")
        String eventType, // STARTED, DELIVERED, FAILED

        String reason,

        String notes
) {}
