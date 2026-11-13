package com.swiftroute.orderservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.swiftroute.orderservice.dto.request.CreatePackageHandoverRequest;
import com.swiftroute.orderservice.dto.request.HandoverActionRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
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
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.packagesForOrder(orderId, requireTenantId(tenantId), userId, role)));
    }

    @GetMapping("/handovers/mine")
    public ResponseEntity<ApiResponse<?>> mine(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.myHandovers(requireTenantId(tenantId), userId, role)));
    }

    @PostMapping("/handovers")
    public ResponseEntity<ApiResponse<?>> create(
            @Valid @RequestBody CreatePackageHandoverRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.createHandover(requireTenantId(tenantId), userId, role, request)));
    }

    @PatchMapping("/handovers/{id}/sender-confirm")
    public ResponseEntity<ApiResponse<?>> senderConfirm(
            @PathVariable UUID id,
            @RequestBody(required = false) HandoverActionRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.senderConfirm(id, requireTenantId(tenantId), userId, role, notes(request))));
    }

    @PatchMapping("/handovers/{id}/receiver-confirm")
    public ResponseEntity<ApiResponse<?>> receiverConfirm(
            @PathVariable UUID id,
            @RequestBody(required = false) HandoverActionRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.receiverConfirm(id, requireTenantId(tenantId), userId, role, notes(request))));
    }

    @PatchMapping("/handovers/{id}/cancel")
    public ResponseEntity<ApiResponse<?>> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) HandoverActionRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(custody.cancel(id, requireTenantId(tenantId), userId, role, notes(request))));
    }

    private UUID requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return tenantId;
    }

    private String notes(HandoverActionRequest request) {
        return request == null ? null : request.notes();
    }
}
