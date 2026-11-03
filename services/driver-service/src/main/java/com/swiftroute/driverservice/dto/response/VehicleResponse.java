package com.swiftroute.driverservice.dto.response;

import java.util.UUID;
import com.swiftroute.driverservice.entity.Vehicle;
import com.swiftroute.driverservice.entity.enums.*;

public record VehicleResponse(UUID id, UUID tenantId, String licensePlate, VehicleType vehicleType,
    Integer capacity, String make, String model, VehicleStatus status, UUID assignedDriverId, String assignedDriverName) {
    public static VehicleResponse fromEntity(Vehicle v) {
        return new VehicleResponse(v.getId(), v.getTenantId(), v.getLicensePlate(), v.getVehicleType(),
            v.getCapacity(), v.getMake(), v.getModel(), v.getStatus(),
            v.getAssignedDriver() == null ? null : v.getAssignedDriver().getId(),
            v.getAssignedDriver() == null ? null : v.getAssignedDriver().getFullName());
    }
}
