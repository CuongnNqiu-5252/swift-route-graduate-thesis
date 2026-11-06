package com.swiftroute.orderservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.orderservice.entity.ReturnRequest;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

    List<ReturnRequest> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ReturnRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<ReturnRequest> findByTenantIdAndStatusInOrderByCreatedAtDesc(UUID tenantId, Collection<String> statuses);

    List<ReturnRequest> findAllByCurrentHolderIdAndStatusInOrderByCreatedAtDesc(UUID driverId, Collection<String> statuses);

    Optional<ReturnRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ReturnRequest> findByReturnTask_Id(UUID taskId);

    Optional<ReturnRequest> findByOrder_Id(UUID orderId);

    boolean existsByOrder_IdAndStatusIn(UUID orderId, Collection<String> statuses);
}
