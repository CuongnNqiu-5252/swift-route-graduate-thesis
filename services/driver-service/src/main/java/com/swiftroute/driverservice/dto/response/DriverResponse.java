package com.swiftroute.driverservice.dto.response;

import java.time.LocalTime;
import java.util.UUID;

import com.swiftroute.driverservice.entity.Driver;
import com.swiftroute.driverservice.entity.enums.DriverStatus;
import com.swiftroute.driverservice.entity.enums.DriverType;
import com.swiftroute.driverservice.entity.enums.VehicleType;

import lombok.Builder;

@Builder
public record DriverResponse(
        UUID id,
        UUID tenantId,
        UUID userId,
        String fullName,
        String phone,
        String licensePlate,
        VehicleType vehicleType,
        Integer capacity,
        DriverType driverType,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        Double currentLat,
        Double currentLng,
        Boolean isAvailable,
        DriverStatus status) {
    public static DriverResponse fromEntity(Driver driver) {
        return DriverResponse.builder()
                .id(driver.getId())
                .tenantId(driver.getTenantId())
                .userId(driver.getUserId())
                .fullName(driver.getFullName())
                .phone(driver.getPhone())
                .licensePlate(driver.getLicensePlate())
                .vehicleType(driver.getVehicleType())
                .capacity(driver.getCapacity())
                .driverType(driver.getDriverType())
                .shiftStart(driver.getShiftStart())
                .shiftEnd(driver.getShiftEnd())
                .currentLat(driver.getCurrentLat())
                .currentLng(driver.getCurrentLng())
                .isAvailable(driver.getIsAvailable())
                .status(driver.getStatus())
                .build();
    }
}
