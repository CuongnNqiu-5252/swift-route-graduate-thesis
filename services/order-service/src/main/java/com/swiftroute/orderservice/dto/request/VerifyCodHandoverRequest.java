package com.swiftroute.orderservice.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VerifyCodHandoverRequest(
        @NotNull UUID operationId,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal countedTotal,
        @Size(max = 1000) String notes) {
}
