package com.swiftroute.driverservice.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.driverservice.dto.request.CreateSettlementRequest;
import com.swiftroute.driverservice.dto.request.MarkSettlementPaidRequest;
import com.swiftroute.driverservice.dto.response.ApiResponse;
import com.swiftroute.driverservice.dto.response.DriverSettlementResponse;
import com.swiftroute.driverservice.service.DriverEarningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN','TENANT_ADMIN','OPS_STAFF','OWNER')")
public class SettlementController {
    private final DriverEarningService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DriverSettlementResponse>>> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.settlements(tenantId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverSettlementResponse>> create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateSettlementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createSettlement(tenantId, request)));
    }

    @PostMapping("/{settlementId}/approve")
    public ResponseEntity<ApiResponse<DriverSettlementResponse>> approve(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID actorId,
            @PathVariable UUID settlementId) {
        return ResponseEntity.ok(ApiResponse.success(service.approveSettlement(tenantId, settlementId, actorId)));
    }

    @PostMapping("/{settlementId}/mark-paid")
    public ResponseEntity<ApiResponse<DriverSettlementResponse>> markPaid(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID actorId,
            @PathVariable UUID settlementId,
            @Valid @RequestBody MarkSettlementPaidRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.markPaid(tenantId, settlementId, actorId, request.paymentReference())));
    }
}
