package com.swiftroute.orderservice.dto.request;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus; import jakarta.validation.constraints.NotNull;
public record UpdateDeliveryTaskRequest(@NotNull DeliveryTaskStatus status, String failureReason, String notes, String proofUrl) {}
