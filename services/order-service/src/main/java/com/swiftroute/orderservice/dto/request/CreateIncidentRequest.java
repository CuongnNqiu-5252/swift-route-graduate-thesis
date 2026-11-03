package com.swiftroute.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateIncidentRequest(
        @NotBlank String incidentType,
        String notes) {}
