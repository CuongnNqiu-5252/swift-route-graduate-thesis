package com.swiftroute.paymentservice.dto.request;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
        @NotNull UUID orderId,
        @NotNull @DecimalMin(value = "0.0") BigDecimal amount,
        String gateway,
        String mode) { }
