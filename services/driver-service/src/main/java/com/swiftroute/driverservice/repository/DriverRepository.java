package com.swiftroute.driverservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.swiftroute.driverservice.entity.Driver;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Driver d WHERE d.id = :id")
    Optional<Driver> findLockedById(UUID id);
    @Query("SELECT d FROM Driver d WHERE d.driverType = :driverType")
    Page<Driver> findByTenantId(UUID tenantId, Pageable pageable);

    List<Driver> findByDriverType(String driverType);

    Optional<Driver> findByUserId(UUID userId);

    Optional<Driver> findByPhone(String phone);

    List<Driver> findAllByTenantId(UUID tenantId);

    List<Driver> findAllByTenantIdAndIsAvailableTrue(UUID tenantId);

    boolean existsByPhone(String phone);

    boolean existsByLicensePlate(String plate);

}
