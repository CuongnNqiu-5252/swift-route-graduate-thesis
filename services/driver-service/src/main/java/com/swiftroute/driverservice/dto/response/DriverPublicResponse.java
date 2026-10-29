package com.swiftroute.driverservice.dto.response;

import java.util.UUID;

import com.swiftroute.driverservice.entity.Driver;
import com.swiftroute.driverservice.entity.enums.VehicleType;

import lombok.Builder;

/**
 * Public driver information for customers.
 * Excludes sensitive information like phone number.
 */
@Builder
public record DriverPublicResponse(
        UUID id,
        String fullName,
        String licensePlate,
        VehicleType vehicleType,
        Double currentLat,
        Double currentLng
) {
    public static DriverPublicResponse fromEntity(Driver driver) {
        return DriverPublicResponse.builder()
                .id(driver.getId())
                .fullName(driver.getFullName())
                .licensePlate(driver.getLicensePlate())
                .vehicleType(driver.getVehicleType())
                .currentLat(driver.getCurrentLat())
                .currentLng(driver.getCurrentLng())
                .build();
    }
}
