package com.swiftroute.orderservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftroute.orderservice.entity.CodHandoverBatch;

public interface CodHandoverBatchRepository extends JpaRepository<CodHandoverBatch, UUID> {
    Optional<CodHandoverBatch> findByTenantIdAndOperationId(UUID tenantId, UUID operationId);
    Optional<CodHandoverBatch> findByTenantIdAndVerificationOperationId(UUID tenantId, UUID operationId);
    Optional<CodHandoverBatch> findByIdAndTenantId(UUID id, UUID tenantId);
    List<CodHandoverBatch> findByTenantIdOrderBySubmittedAtDesc(UUID tenantId);
    List<CodHandoverBatch> findByTenantIdAndDriverIdOrderBySubmittedAtDesc(UUID tenantId, UUID driverId);
    List<CodHandoverBatch> findByTenantIdAndStatusOrderBySubmittedAtDesc(UUID tenantId, String status);
}
