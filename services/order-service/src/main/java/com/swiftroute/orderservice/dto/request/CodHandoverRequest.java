package com.swiftroute.orderservice.dto.request; import java.math.BigDecimal; import jakarta.validation.constraints.*;
public record CodHandoverRequest(@NotNull @DecimalMin("0.0") BigDecimal amount, String notes) {}
