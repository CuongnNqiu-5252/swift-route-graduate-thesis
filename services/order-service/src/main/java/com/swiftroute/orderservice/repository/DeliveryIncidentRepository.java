package com.swiftroute.orderservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.swiftroute.orderservice.entity.DeliveryIncident;

@Repository
public interface DeliveryIncidentRepository extends JpaRepository<DeliveryIncident, UUID> {

    List<DeliveryIncident> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<DeliveryIncident> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<DeliveryIncident> findByTenantIdAndStatusInOrderByCreatedAtDesc(UUID tenantId, Collection<String> statuses);

    List<DeliveryIncident> findAllByOrder_IdOrderByCreatedAtDesc(UUID orderId);

    List<DeliveryIncident> findAllByDispatchPlan_IdOrderByCreatedAtDesc(UUID planId);

    List<DeliveryIncident> findAllByDriverIdOrderByCreatedAtDesc(UUID driverId);

    @Query("""
            select item from DeliveryIncident item
            where item.tenantId = :tenantId
              and (:status is null or upper(item.status) = :status)
              and (:driverId is null or item.driverId = :driverId)
              and (:orderId is null or item.order.id = :orderId)
            """)
    Page<DeliveryIncident> findPage(UUID tenantId, String status, UUID driverId, UUID orderId, Pageable pageable);

    Optional<DeliveryIncident> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByOrder_IdAndStatusIn(UUID orderId, Collection<String> statuses);

    boolean existsByDispatchPlan_IdAndIncidentTypeAndStatusIn(
            UUID planId, String incidentType, Collection<String> statuses);
}
