package com.swiftroute.orderservice.controller;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.DeliveryReportResponse;
import com.swiftroute.orderservice.dto.response.PageResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;
import com.swiftroute.orderservice.service.DeliveryReportService;
import com.swiftroute.orderservice.service.DeliveryReportService.DetailKind;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders/reports/delivery")
@RequiredArgsConstructor
public class DeliveryReportController {
    private static final Set<String> REPORT_ROLES = Set.of("SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR");
    private final DeliveryReportService reports;

    @GetMapping
    public ApiResponse<DeliveryReportResponse> overview(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant,
            @RequestAttribute("effectiveRole") String role,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID warehouse) {
        requireAccess(tenant, role);
        return ApiResponse.success(reports.overview(tenant, warehouse, from, to));
    }

    @GetMapping("/details")
    public ApiResponse<PageResponse<DeliveryReportResponse.Detail>> details(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant,
            @RequestAttribute("effectiveRole") String role,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID warehouse,
            @RequestParam DetailKind kind,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page) {
        requireAccess(tenant, role);
        return ApiResponse.success(reports.details(tenant, warehouse, from, to, kind, reason, page));
    }

    private void requireAccess(UUID tenant, String role) {
        if (tenant == null || role == null || !REPORT_ROLES.contains(role)
                || !TenantRequestInterceptor.isAuthorizedForPermission(
                        role, "order:read", REPORT_ROLES)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
