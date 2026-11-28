package com.swiftroute.orderservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.enums.PlanStatus;

public interface DispatchPlanRepository extends JpaRepository<DispatchPlan, UUID> {

        /** All plans for a tenant, newest first. */
        Page<DispatchPlan> findByTenantId(UUID tenantId, Pageable pageable);

        List<DispatchPlan> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

        Optional<DispatchPlan> findByIdAndTenantId(UUID id, UUID tenantId);

        /** Active (non-terminal) plans for a tenant. */
        List<DispatchPlan> findAllByTenantIdAndPlanStatusIn(UUID tenantId, List<PlanStatus> statuses);

        /** Plans assigned to a specific driver that are not yet terminal. */
        List<DispatchPlan> findAllByDriverIdAndPlanStatusIn(UUID driverId, List<PlanStatus> statuses);

        /**
         * Plans assigned to a specific driver within a specific tenant that are in the
         * given statuses.
         */
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
         * Find DRAFT plans created before a certain timestamp (stale drafts that were
         * never approved or cancelled — safety cleanup).
         */
        @Query("""
                        SELECT p FROM DispatchPlan p
                        WHERE p.planStatus = 'DRAFT'
                          AND p.createdAt < :cutoff
                        """)
        List<DispatchPlan> findStaleDraftPlans(@Param("cutoff") Instant cutoff);

        @Modifying
        @Query(value = "UPDATE delivery_incidents SET dispatch_plan_id = NULL WHERE dispatch_plan_id = :planId", nativeQuery = true)
        void clearIncidentReferences(@Param("planId") UUID planId);

        @Modifying
        @Query(value = """
                        UPDATE delivery_tasks
                        SET dispatch_plan_id = NULL,
                            status = CASE
                                WHEN status = 'OUT_FOR_DELIVERY' THEN 'CANCELLED'
                                ELSE status
                            END,
                            updated_at = NOW()
                        WHERE dispatch_plan_id = :planId
                        """, nativeQuery = true)
        void clearTaskReferences(@Param("planId") UUID planId);
}
