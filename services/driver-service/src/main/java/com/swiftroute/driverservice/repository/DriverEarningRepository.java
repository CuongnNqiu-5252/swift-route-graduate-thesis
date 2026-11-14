package com.swiftroute.driverservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swiftroute.driverservice.entity.DriverEarning;
import com.swiftroute.driverservice.entity.enums.EarningStatus;

public interface DriverEarningRepository extends JpaRepository<DriverEarning, UUID> {
    Optional<DriverEarning> findByOrderId(UUID orderId);
    Page<DriverEarning> findByDriverIdAndCompletedAtBetween(UUID scopeId, Instant from, Instant to, Pageable pageable);

    Page<DriverEarning> findByTenantIdAndCompletedAtBetween(UUID scopeId, Instant from, Instant to, Pageable pageable);

    List<DriverEarning> findByDriverIdAndCompletedAtBetweenOrderByCompletedAtDesc(UUID driverId, Instant from, Instant to);
    List<DriverEarning> findByTenantIdAndCompletedAtBetweenOrderByCompletedAtDesc(UUID tenantId, Instant from, Instant to);
    List<DriverEarning> findByTenantIdAndDriverIdAndStatusAndSettlementIdIsNullAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            UUID tenantId, UUID driverId, EarningStatus status, Instant from, Instant to);
    List<DriverEarning> findBySettlementIdOrderByCompletedAtAsc(UUID settlementId);
}
