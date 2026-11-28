package com.swiftroute.driverservice.controller;

import org.springframework.web.bind.annotation.RequestParam;
import com.swiftroute.driverservice.dto.response.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.driverservice.dto.request.UpdateEarningPolicyRequest;
import com.swiftroute.driverservice.dto.response.*;
import com.swiftroute.driverservice.exception.AppException;
import com.swiftroute.driverservice.exception.ErrorCode;
import com.swiftroute.driverservice.service.DriverEarningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/earnings")
@RequiredArgsConstructor
public class EarningController {
    private final DriverEarningService service;

    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<List<DriverEarningResponse>>> mine(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(service.myEarnings(resolveUserId(authentication), from, to)));
    }

    @GetMapping("/me/summary")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<EarningSummaryResponse>> mySummary(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(service.mySummary(resolveUserId(authentication), from, to)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OPERATOR','OWNER','VIEWER')")
    public ResponseEntity<ApiResponse<List<DriverEarningResponse>>> tenantEarnings(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(service.tenantEarnings(requireTenantId(tenantId), from, to)));
    }

    @GetMapping("/me/page")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<PageResponse<DriverEarningResponse>> myEarningPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.success(service.myEarningPage(resolveUserId(authentication), from, to, page, size));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OPERATOR','OWNER','VIEWER')")
    public ApiResponse<PageResponse<DriverEarningResponse>> tenantEarningPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.success(service.tenantEarningPage(requireTenantId(tenantId), from, to, page, size));
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OPERATOR','OWNER','VIEWER')")
    public ResponseEntity<ApiResponse<EarningPolicyResponse>> policy(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.policy(requireTenantId(tenantId))));
    }

    @PutMapping("/policy")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OWNER')")
    public ResponseEntity<ApiResponse<EarningPolicyResponse>> updatePolicy(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @Valid @RequestBody UpdateEarningPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updatePolicy(requireTenantId(tenantId), request)));
    }

    private UUID resolveUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private UUID requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return tenantId;
    }
}
