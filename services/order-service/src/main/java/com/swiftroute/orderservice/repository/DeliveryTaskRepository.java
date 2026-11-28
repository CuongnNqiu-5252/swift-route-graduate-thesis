package com.swiftroute.orderservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;

public interface DeliveryTaskRepository extends JpaRepository<DeliveryTask, UUID> {
    Optional<DeliveryTask> findByOrder_IdAndTaskType(UUID orderId, String taskType);

    List<DeliveryTask> findByDispatchPlanId(UUID dispatchPlanId);

    @Query("""
            select distinct task.dispatchPlanId from DeliveryTask task
            where task.tenantId = :tenantId and task.dispatchPlanId in :planIds
              and task.taskType = 'DELIVERY' and task.status = :status
            """)
    List<UUID> findPlanIdsWithDeliveryStatus(@Param("tenantId") UUID tenantId,
            @Param("planIds") Collection<UUID> planIds, @Param("status") DeliveryTaskStatus status);

    List<DeliveryTask> findByDriverIdAndStatusIn(UUID driverId, Collection<DeliveryTaskStatus> statuses);

    List<DeliveryTask> findByTenantIdAndDriverIdAndStatusAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDesc(
            UUID tenantId, UUID driverId, DeliveryTaskStatus status, Instant startOfDay);

    @Query("select distinct task.order.id from DeliveryTask task "
            + "where task.driverId = :driverId and task.status in :statuses")
    List<UUID> findActiveOrderIdsByDriverId(
            @Param("driverId") UUID driverId,
            @Param("statuses") Collection<DeliveryTaskStatus> statuses);

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
              AND order_id IN (:orderIds)
            """, nativeQuery = true)
    void cancelTasksForPlanOrders(
            @Param("planId") UUID planId,
            @Param("orderIds") Collection<UUID> orderIds);
}
