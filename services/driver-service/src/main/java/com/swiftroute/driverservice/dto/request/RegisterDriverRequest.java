package com.swiftroute.driverservice.dto.request;

import java.time.LocalTime;
import java.util.UUID;

import com.swiftroute.driverservice.entity.enums.DriverType;
import com.swiftroute.driverservice.entity.enums.VehicleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDriverRequest(
                UUID userId,

                @NotNull(message = "tenant_id is required") UUID tenantId,

                @NotBlank(message = "full_name is required") @Size(max = 255) String fullName,

                @NotBlank(message = "phone is required") @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "phone format is invalid") String phone,

                @NotBlank(message = "license_plate is required") @Size(max = 20) String licensePlate,

                @NotNull(message = "vehicle_type is required") VehicleType vehicleType,

                Integer capacity,

                @NotNull(message = "driver_type is required") DriverType driverType,

                LocalTime shiftStart,

                LocalTime shiftEnd) {
}
