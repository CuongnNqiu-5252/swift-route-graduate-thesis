package com.swiftroute.driverservice.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swiftroute.driverservice.entity.DriverSettlement;

public interface DriverSettlementRepository extends JpaRepository<DriverSettlement, UUID> {
    List<DriverSettlement> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
