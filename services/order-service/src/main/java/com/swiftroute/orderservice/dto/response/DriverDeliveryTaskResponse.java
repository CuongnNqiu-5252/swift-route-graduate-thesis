package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;

public record DriverDeliveryTaskResponse(
        UUID taskId,
        UUID orderId,
        String taskType,
        DeliveryTaskStatus status,
        long version,
        Instant updatedAt,
        OrderResponse order) {
    public static DriverDeliveryTaskResponse from(DeliveryTask task) {
        return new DriverDeliveryTaskResponse(
                task.getId(), task.getOrder().getId(), task.getTaskType(), task.getStatus(),
                task.getVersion() == null ? 0 : task.getVersion(), task.getUpdatedAt(),
                OrderResponse.fromEntity(task.getOrder()));
    }
}
