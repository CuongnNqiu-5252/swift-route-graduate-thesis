package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.BatchDeliveryTaskActionRequest;
import com.swiftroute.orderservice.dto.request.UpdateDeliveryTaskRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.DriverDeliveryTaskResponse;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.service.DeliveryExecutionService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/delivery-tasks")
@RequiredArgsConstructor
public class DeliveryTaskController {
    private final DeliveryExecutionService execution;

    @Operation(summary = "List active delivery and return tasks assigned to the authenticated driver")
    @GetMapping("/mine")
    public ResponseEntity<?> mine(@RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(execution.activeForDriver(userId, role));
    }

    @Operation(summary = "Accept all or selected assigned delivery tasks in a single driver action")
    @PostMapping("/batch-accept")
    public ResponseEntity<ApiResponse<List<DriverDeliveryTaskResponse>>> batchAccept(
            @RequestBody(required = false) BatchDeliveryTaskActionRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(execution.batchAccept(userId, role, request)));
    }

    @Operation(summary = "Confirm physical pickup for grouped depot tasks in one swipe and start delivery")
    @PostMapping("/batch-pickup")
    public ResponseEntity<ApiResponse<List<DriverDeliveryTaskResponse>>> batchPickup(
            @RequestBody(required = false) BatchDeliveryTaskActionRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(execution.batchPickup(userId, role, request)));
    }

    @Operation(summary = "Update status of an individual delivery task")
    @PatchMapping("/{taskId}")
    public ResponseEntity<DeliveryTask> update(@PathVariable UUID taskId,
            @Valid @RequestBody UpdateDeliveryTaskRequest request, @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(execution.update(taskId, userId, role, request));
    }

    @Operation(summary = "Update status of the delivery task associated with a specific order")
    @PatchMapping("/order/{orderId}")
    public ResponseEntity<DeliveryTask> updateOrderTask(@PathVariable UUID orderId,
            @Valid @RequestBody UpdateDeliveryTaskRequest request, @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(execution.updateForOrder(orderId, userId, role, request));
    }
}
