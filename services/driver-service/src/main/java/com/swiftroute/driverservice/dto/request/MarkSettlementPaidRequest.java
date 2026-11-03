package com.swiftroute.driverservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkSettlementPaidRequest(@NotBlank @Size(max = 120) String paymentReference) {}
