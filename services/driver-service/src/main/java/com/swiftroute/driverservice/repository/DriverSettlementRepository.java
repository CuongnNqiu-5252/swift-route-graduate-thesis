package com.swiftroute.driverservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swiftroute.driverservice.entity.DriverSettlement;

public interface DriverSettlementRepository extends JpaRepository<DriverSettlement, UUID> {
    Page<DriverSettlement> findByTenantId(UUID tenantId, Pageable pageable);

    List<DriverSettlement> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
