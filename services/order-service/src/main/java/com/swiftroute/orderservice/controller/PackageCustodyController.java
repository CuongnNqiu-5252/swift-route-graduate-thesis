package com.swiftroute.orderservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.swiftroute.orderservice.dto.request.CreatePackageHandoverRequest;
import com.swiftroute.orderservice.dto.request.HandoverActionRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.service.PackageCustodyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/custody")
@RequiredArgsConstructor
public class PackageCustodyController {
    private final PackageCustodyService custody;

    @GetMapping("/orders/{orderId}/packages")
    public ResponseEntity<ApiResponse<?>> packages(
            @PathVariable UUID orderId,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.packagesForOrder(orderId, tenantId, userId, role)));
    }

    @GetMapping("/handovers/mine")
    public ResponseEntity<ApiResponse<?>> mine(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.myHandovers(tenantId, userId, role)));
    }

    @PostMapping("/handovers")
    public ResponseEntity<ApiResponse<?>> create(
            @Valid @RequestBody CreatePackageHandoverRequest request,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.createHandover(tenantId, userId, role, request)));
    }

    @PatchMapping("/handovers/{id}/sender-confirm")
    public ResponseEntity<ApiResponse<?>> senderConfirm(
            @PathVariable UUID id,
            @RequestBody(required = false) HandoverActionRequest request,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.senderConfirm(id, tenantId, userId, role, notes(request))));
    }

    @PatchMapping("/handovers/{id}/receiver-confirm")
    public ResponseEntity<ApiResponse<?>> receiverConfirm(
            @PathVariable UUID id,
            @RequestBody(required = false) HandoverActionRequest request,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.receiverConfirm(id, tenantId, userId, role, notes(request))));
    }

    @PatchMapping("/handovers/{id}/cancel")
    public ResponseEntity<ApiResponse<?>> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) HandoverActionRequest request,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.cancel(id, tenantId, userId, role, notes(request))));
    }

    private String notes(HandoverActionRequest request) {
        return request == null ? null : request.notes();
    }
}
