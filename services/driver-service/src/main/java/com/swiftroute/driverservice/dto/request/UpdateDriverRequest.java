package com.swiftroute.driverservice.dto.request;

import com.swiftroute.driverservice.entity.enums.DriverType;
import com.swiftroute.driverservice.entity.enums.VehicleType;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record UpdateDriverRequest(
        @Size(max = 255)
        String fullName,

        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "phone format is invalid")
        String phone,

        @Size(max = 20)
        String licensePlate,

        VehicleType vehicleType,

        Integer capacity,

        DriverType driverType,

        LocalTime shiftStart,

        LocalTime shiftEnd
) {}
