package com.swiftroute.orderservice.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordCodCollectionRequest(
        @NotNull UUID operationId,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal amount,
        @NotBlank String outcome,
        @Size(max = 500) String reason,
        String evidenceUrl,
        Instant occurredAt) {
}
