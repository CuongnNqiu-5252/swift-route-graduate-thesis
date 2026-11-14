package com.swiftroute.orderservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.enums.PlanStatus;

public interface DispatchPlanRepository extends JpaRepository<DispatchPlan, UUID> {

    /** All plans for a tenant, newest first. */
    Page<DispatchPlan> findByTenantId(UUID tenantId, Pageable pageable);

    List<DispatchPlan> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** Active (non-terminal) plans for a tenant. */
    List<DispatchPlan> findAllByTenantIdAndPlanStatusIn(UUID tenantId, List<PlanStatus> statuses);

    /** Plans assigned to a specific driver that are not yet terminal. */
    List<DispatchPlan> findAllByDriverIdAndPlanStatusIn(UUID driverId, List<PlanStatus> statuses);

    /** Plans assigned to a specific driver within a specific tenant that are in the given statuses. */
    @Query("""
            SELECT p FROM DispatchPlan p
            WHERE p.tenant.id = :tenantId
              AND p.driverId = :driverId
              AND p.planStatus IN :statuses
            """)
    List<DispatchPlan> findAllByTenantIdAndDriverIdAndPlanStatusIn(
            @Param("tenantId") UUID tenantId,
            @Param("driverId") UUID driverId,
            @Param("statuses") List<PlanStatus> statuses);

    /** All plans in a specific lifecycle status across tenants. */
    List<DispatchPlan> findAllByPlanStatus(PlanStatus planStatus);

    /**
     * Find DISPATCHED plans whose acknowledgement deadline has passed and driver
     * has not yet acknowledged. Used by the ack-timeout scheduler.
     */
    @Query("""
            SELECT p FROM DispatchPlan p
            WHERE p.planStatus = 'DISPATCHED'
              AND p.ackDeadlineAt IS NOT NULL
              AND p.ackDeadlineAt < :now
              AND p.acknowledgedAt IS NULL
              AND p.declinedAt IS NULL
            """)
    List<DispatchPlan> findTimedOutDispatchedPlans(@Param("now") Instant now);

    /**
     * Find DRAFT plans created before a certain timestamp (stale drafts that were
     * never approved or cancelled — safety cleanup).
     */
    @Query("""
            SELECT p FROM DispatchPlan p
            WHERE p.planStatus = 'DRAFT'
              AND p.createdAt < :cutoff
            """)
    List<DispatchPlan> findStaleDraftPlans(@Param("cutoff") Instant cutoff);
}

