package com.swiftroute.driverservice.dto.request;

import java.util.UUID;
import com.swiftroute.driverservice.entity.enums.VehicleStatus;
import com.swiftroute.driverservice.entity.enums.VehicleType;
import jakarta.validation.constraints.*;

public record UpsertVehicleRequest(
    @NotNull UUID tenantId,
    @NotBlank String licensePlate,
    @NotNull VehicleType vehicleType,
    @PositiveOrZero Integer capacity,
    String make,
    String model,
    VehicleStatus status,
    UUID assignedDriverId
) {}
