package com.swiftroute.orderservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.UpdateDeliveryTaskRequest;
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

    @Operation(summary = "List the driver's active tasks and optionally today's completed tasks")
    @GetMapping("/mine")
    public ResponseEntity<?> mine(@RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestParam(name = "include_completed", defaultValue = "false") boolean includeCompleted) {
        if (includeCompleted) {
            return ResponseEntity.ok(execution.tasksForDriverToday(userId, role, tenantId));
        }
        return ResponseEntity.ok(execution.activeForDriver(userId, role));
    }

    @Operation(summary = "Update status of an individual delivery task")
    @PatchMapping("/{taskId}")
    public ResponseEntity<DeliveryTask> update(@PathVariable UUID taskId,
            @Valid @RequestBody UpdateDeliveryTaskRequest request, @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(execution.update(taskId, userId, role, request));
    }
}
