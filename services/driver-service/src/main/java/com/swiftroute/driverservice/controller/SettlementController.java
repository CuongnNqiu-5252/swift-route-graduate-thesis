package com.swiftroute.driverservice.controller;

import org.springframework.web.bind.annotation.RequestParam;
import com.swiftroute.driverservice.dto.response.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.driverservice.dto.request.CreateSettlementRequest;
import com.swiftroute.driverservice.dto.request.MarkSettlementPaidRequest;
import com.swiftroute.driverservice.dto.response.ApiResponse;
import com.swiftroute.driverservice.dto.response.DriverSettlementResponse;
import com.swiftroute.driverservice.exception.AppException;
import com.swiftroute.driverservice.exception.ErrorCode;
import com.swiftroute.driverservice.service.DriverEarningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OPERATOR','OWNER','VIEWER')")
public class SettlementController {
    private final DriverEarningService service;

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OPERATOR','OWNER','VIEWER')")
    public ApiResponse<PageResponse<DriverSettlementResponse>> settlementPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        return ApiResponse.success(service.settlementPage(requireTenantId(tenantId), page, size));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OPERATOR','OWNER','VIEWER')")
    public ResponseEntity<ApiResponse<List<DriverSettlementResponse>>> list(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.settlements(requireTenantId(tenantId))));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OPERATOR','OWNER')")
    public ResponseEntity<ApiResponse<DriverSettlementResponse>> create(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @Valid @RequestBody CreateSettlementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createSettlement(requireTenantId(tenantId), request)));
    }

    @PostMapping("/{settlementId}/approve")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OWNER')")
    public ResponseEntity<ApiResponse<DriverSettlementResponse>> approve(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            Authentication authentication,
            @PathVariable UUID settlementId) {
        return ResponseEntity.ok(ApiResponse.success(
                service.approveSettlement(requireTenantId(tenantId), settlementId, UUID.fromString(authentication.getName()))));
    }

    @PostMapping("/{settlementId}/mark-paid")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OWNER')")
    public ResponseEntity<ApiResponse<DriverSettlementResponse>> markPaid(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            Authentication authentication,
            @PathVariable UUID settlementId,
            @Valid @RequestBody MarkSettlementPaidRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.markPaid(requireTenantId(tenantId), settlementId, UUID.fromString(authentication.getName()), request.paymentReference())));
    }

    private UUID requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return tenantId;
    }
}
