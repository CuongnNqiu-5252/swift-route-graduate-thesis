package com.swiftroute.driverservice.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpdateEarningPolicyRequest(
        @NotNull @DecimalMin("0") BigDecimal baseAmount,
        @NotNull @DecimalMin("0") BigDecimal perKmAmount,
        @NotNull @DecimalMin("0") BigDecimal minimumAmount,
        @NotNull @DecimalMin("0.01") BigDecimal heavyWeightThresholdKg,
        @NotNull @DecimalMin("0") BigDecimal heavyBonusAmount) {}
