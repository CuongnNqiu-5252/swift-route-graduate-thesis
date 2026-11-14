package com.swiftroute.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HandoverDecisionRequest(
        @NotBlank @Size(max = 1000) String notes) {
}
