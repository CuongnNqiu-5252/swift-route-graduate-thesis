package com.swiftroute.driverservice.dto.request;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.swiftroute.driverservice.entity.enums.DriverType;
import com.swiftroute.driverservice.entity.enums.VehicleType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateDriverRequest(
        @JsonAlias({"fullName", "full_name"})
        @Size(max = 255)
        String fullName,

        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "phone format is invalid")
        String phone,

        @Email(message = "email format is invalid")
        @Size(max = 255)
        String email,

        @JsonAlias({"licensePlate", "license_plate"})
        @Size(max = 20)
        String licensePlate,

        @JsonAlias({"vehicleType", "vehicle_type"})
        VehicleType vehicleType,

        @jakarta.validation.constraints.Positive Integer capacity,

        @JsonAlias({"driverType", "driver_type"})
        DriverType driverType,

        @JsonAlias({"shiftStart", "shift_start"})
        LocalTime shiftStart,

        @JsonAlias({"shiftEnd", "shift_end"})
        LocalTime shiftEnd,

        @JsonAlias({"bankCode", "bank_code"})
        @Pattern(regexp = "^[A-Za-z0-9]{2,20}$", message = "bank_code format is invalid")
        String bankCode,

        @JsonAlias({"bankAccountNumber", "bank_account_number"})
        @Pattern(regexp = "^[0-9A-Za-z]{4,40}$", message = "bank_account_number format is invalid")
        String bankAccountNumber,

        @JsonAlias({"bankAccountName", "bank_account_name"})
        @Size(max = 120)
        String bankAccountName) {

    public UpdateDriverRequest(
            String fullName,
            String phone,
            String licensePlate,
            VehicleType vehicleType,
            Integer capacity,
            DriverType driverType,
            LocalTime shiftStart,
            LocalTime shiftEnd,
            String bankCode,
            String bankAccountNumber,
            String bankAccountName) {
        this(fullName, phone, null, licensePlate, vehicleType, capacity, driverType,
                shiftStart, shiftEnd, bankCode, bankAccountNumber, bankAccountName);
    }
}
