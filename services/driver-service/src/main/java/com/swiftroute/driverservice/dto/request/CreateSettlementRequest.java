package com.swiftroute.driverservice.dto.request;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record CreateSettlementRequest(@NotNull UUID driverId, @NotNull Instant periodStart, @NotNull Instant periodEnd) {}
