package com.swiftroute.driverservice.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.driverservice.dto.request.UpdateEarningPolicyRequest;
import com.swiftroute.driverservice.dto.response.*;
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
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(service.myEarnings(userId, from, to)));
    }

    @GetMapping("/me/summary")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<EarningSummaryResponse>> mySummary(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(service.mySummary(userId, from, to)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN','TENANT_ADMIN','OPS_STAFF','OWNER')")
    public ResponseEntity<ApiResponse<List<DriverEarningResponse>>> tenantEarnings(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(service.tenantEarnings(tenantId, from, to)));
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN','TENANT_ADMIN','OPS_STAFF','OWNER')")
    public ResponseEntity<ApiResponse<EarningPolicyResponse>> policy(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.policy(tenantId)));
    }

    @PutMapping("/policy")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ApiResponse<EarningPolicyResponse>> updatePolicy(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody UpdateEarningPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updatePolicy(tenantId, request)));
    }
}
