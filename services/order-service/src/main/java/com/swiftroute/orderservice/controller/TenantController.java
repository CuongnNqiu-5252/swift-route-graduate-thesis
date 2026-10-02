package com.swiftroute.orderservice.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.CreateTenantRequest;
import com.swiftroute.orderservice.dto.request.TenantStatusRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.TenantResponse;
import com.swiftroute.orderservice.service.TenantService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Tenant onboarding and platform-review endpoints. */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;

    @Operation(summary = "Create a pending tenant onboarding request")
    @PostMapping
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(
            @Valid @RequestBody CreateTenantRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        TenantResponse response = tenantService.createTenant(request, UUID.fromString(userId), userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Get a tenant visible to its owner or platform staff")
    @GetMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenant(
            @PathVariable UUID tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.getTenant(tenantId, UUID.fromString(userId), userRole)));
    }

    @Operation(summary = "Approve, reject, suspend, or reactivate a tenant")
    @PatchMapping("/{tenantId}/status")
    public ResponseEntity<ApiResponse<TenantResponse>> changeStatus(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantStatusRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(ApiResponse.success(
                tenantService.changeStatus(tenantId, request, UUID.fromString(userId), userRole)));
    }
}
