package com.swiftroute.orderservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;

public interface DeliveryTaskRepository extends JpaRepository<DeliveryTask, UUID> {
    Optional<DeliveryTask> findByOrder_IdAndTaskType(UUID orderId, String taskType);

    List<DeliveryTask> findByDispatchPlanId(UUID dispatchPlanId);

    List<DeliveryTask> findByDriverIdAndStatusIn(UUID driverId, Collection<DeliveryTaskStatus> statuses);

    @Query("select distinct task.order.id from DeliveryTask task "
            + "where task.driverId = :driverId and task.status in :statuses")
    List<UUID> findActiveOrderIdsByDriverId(
            @Param("driverId") UUID driverId,
            @Param("statuses") Collection<DeliveryTaskStatus> statuses);
}
