package com.swiftroute.orderservice.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveCodDisputeRequest(
        @NotNull UUID operationId,
        @NotBlank String resolutionType,
        @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal adjustmentAmount,
        @NotBlank @Size(max = 1000) String notes,
        String evidenceUrl) {
}
