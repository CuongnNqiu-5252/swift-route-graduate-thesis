package com.swiftroute.driverservice.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swiftroute.driverservice.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    boolean existsByLicensePlate(String licensePlate);
}
