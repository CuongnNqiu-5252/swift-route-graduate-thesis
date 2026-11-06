package com.swiftroute.driverservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swiftroute.driverservice.entity.DriverEarning;
import com.swiftroute.driverservice.entity.enums.EarningStatus;

public interface DriverEarningRepository extends JpaRepository<DriverEarning, UUID> {
    Optional<DriverEarning> findByOrderId(UUID orderId);
    List<DriverEarning> findByDriverIdAndCompletedAtBetweenOrderByCompletedAtDesc(UUID driverId, Instant from, Instant to);
    List<DriverEarning> findByTenantIdAndCompletedAtBetweenOrderByCompletedAtDesc(UUID tenantId, Instant from, Instant to);
    List<DriverEarning> findByTenantIdAndDriverIdAndStatusAndSettlementIdIsNullAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            UUID tenantId, UUID driverId, EarningStatus status, Instant from, Instant to);
    List<DriverEarning> findBySettlementIdOrderByCompletedAtAsc(UUID settlementId);
}
