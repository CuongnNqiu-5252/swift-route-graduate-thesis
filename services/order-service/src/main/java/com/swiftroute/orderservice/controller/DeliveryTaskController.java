package com.swiftroute.orderservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.UpdateDeliveryTaskRequest;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.service.DeliveryExecutionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/delivery-tasks")
@RequiredArgsConstructor
public class DeliveryTaskController {
    private final DeliveryExecutionService execution;

    @GetMapping("/mine")
    public ResponseEntity<?> mine(@RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(execution.activeForDriver(userId, role));
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<DeliveryTask> update(@PathVariable UUID taskId,
            @Valid @RequestBody UpdateDeliveryTaskRequest request, @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(execution.update(taskId, userId, role, request));
    }

    @PatchMapping("/order/{orderId}")
    public ResponseEntity<DeliveryTask> updateOrderTask(@PathVariable UUID orderId,
            @Valid @RequestBody UpdateDeliveryTaskRequest request, @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(execution.updateForOrder(orderId, userId, role, request));
    }
}
