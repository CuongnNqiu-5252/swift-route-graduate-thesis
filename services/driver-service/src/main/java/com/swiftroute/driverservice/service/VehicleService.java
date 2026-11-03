package com.swiftroute.driverservice.service;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.swiftroute.driverservice.dto.request.*;
import com.swiftroute.driverservice.dto.response.VehicleResponse;
import com.swiftroute.driverservice.entity.*;
import com.swiftroute.driverservice.entity.enums.VehicleStatus;
import com.swiftroute.driverservice.exception.*;
import com.swiftroute.driverservice.repository.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class VehicleService {
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(UUID tenantId) {
        com.swiftroute.driverservice.security.GatewayHeaderAuthFilter.assertTenant(tenantId);
        return vehicles.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(VehicleResponse::fromEntity).toList();
    }

    @Transactional
    public VehicleResponse create(UpsertVehicleRequest r) {
        com.swiftroute.driverservice.security.GatewayHeaderAuthFilter.assertTenant(r.tenantId());
        if (vehicles.existsByLicensePlate(r.licensePlate().trim().toUpperCase())) throw new AppException(ErrorCode.DRIVER_ALREADY_EXISTS);
        Vehicle v = Vehicle.builder().tenantId(r.tenantId()).licensePlate(r.licensePlate().trim().toUpperCase())
            .vehicleType(r.vehicleType()).capacity(r.capacity()).make(r.make()).model(r.model()).build();
        applyAssignment(v, r.assignedDriverId());
        if (r.status() != null && v.getAssignedDriver() == null) v.setStatus(r.status());
        return VehicleResponse.fromEntity(vehicles.save(v));
    }

    @Transactional
    public VehicleResponse update(UUID id, UpsertVehicleRequest r) {
        Vehicle v = get(id); v.setLicensePlate(r.licensePlate().trim().toUpperCase()); v.setVehicleType(r.vehicleType());
        v.setCapacity(r.capacity()); v.setMake(r.make()); v.setModel(r.model()); v.setUpdatedAt(Instant.now());
        applyAssignment(v, r.assignedDriverId());
        if (v.getAssignedDriver() == null && r.status() != null) v.setStatus(r.status());
        return VehicleResponse.fromEntity(vehicles.save(v));
    }

    @Transactional
    public VehicleResponse assign(UUID id, UUID driverId) {
        Vehicle v = get(id); applyAssignment(v, driverId); v.setUpdatedAt(Instant.now());
        return VehicleResponse.fromEntity(vehicles.save(v));
    }

    private Vehicle get(UUID id) { Vehicle v=vehicles.findById(id).orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));com.swiftroute.driverservice.security.GatewayHeaderAuthFilter.assertTenant(v.getTenantId());return v; }
    private void applyAssignment(Vehicle v, UUID driverId) {
        if (driverId == null) { v.setAssignedDriver(null); if (v.getStatus() == VehicleStatus.ASSIGNED) v.setStatus(VehicleStatus.AVAILABLE); return; }
        Driver d = drivers.findById(driverId).orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        if (!d.getTenantId().equals(v.getTenantId())) throw new AppException(ErrorCode.ACCESS_DENIED);
        v.setAssignedDriver(d); v.setStatus(VehicleStatus.ASSIGNED);
    }
}
