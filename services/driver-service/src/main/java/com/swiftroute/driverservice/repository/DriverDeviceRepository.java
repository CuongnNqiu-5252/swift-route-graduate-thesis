package com.swiftroute.driverservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.driverservice.entity.DriverDevice;

@Repository
public interface DriverDeviceRepository extends JpaRepository<DriverDevice, UUID> {

    Optional<DriverDevice> findByTenantIdAndDriverIdAndRevokedAtIsNull(UUID tenantId, UUID driverId);

    Optional<DriverDevice> findBySessionId(UUID sessionId);

    List<DriverDevice> findByTenantIdAndDriverIdOrderByActivatedAtDesc(UUID tenantId, UUID driverId);

    List<DriverDevice> findByTenantIdAndDriverIdInOrderByActivatedAtDesc(UUID tenantId, Collection<UUID> driverIds);
}
