package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;
import com.swiftroute.orderservice.service.OrderImportService;
import com.swiftroute.orderservice.service.OrderImportService.Address;
import com.swiftroute.orderservice.service.OrderImportService.Pending;
import com.swiftroute.orderservice.service.OrderImportService.Preview;
import com.swiftroute.orderservice.service.OrderImportService.Result;
import com.swiftroute.orderservice.service.OrderImportService.Row;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders/import")
@RequiredArgsConstructor
public class OrderImportController {
    private final OrderImportService imports;

    public record Complete(String externalOrderCode, Address address) {
    }

    public record Cancel(String externalOrderCode) {
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<Result>> submit(@RequestBody List<Row> rows,
            @RequestAttribute("authorizedTenantId") UUID tenant, @RequestAttribute("authorizedUserId") UUID user,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requireCreate(role);
        if (rows.isEmpty() || rows.size() > 200 || rows.stream().anyMatch(java.util.Objects::isNull)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return ApiResponse.success(imports.submit(rows, tenant, user, role));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nhập danh sách vận đơn trực tiếp từ file Excel (.xlsx)")
    public ApiResponse<List<Result>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("authorizedTenantId") UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requireCreate(role);
        return ApiResponse.success(imports.importExcel(file, tenantId, userId, role));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nhập danh sách vận đơn trực tiếp từ file Excel (.xlsx) qua /file")
    public ApiResponse<List<Result>> importExcelFile(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("authorizedTenantId") UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requireCreate(role);
        return ApiResponse.success(imports.importExcel(file, tenantId, userId, role));
    }

    @PostMapping(value = "/file/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Preview> previewExcel(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requireCreate(role);
        return ApiResponse.success(imports.previewExcel(file));
    }

    @GetMapping("/pending")
    public ApiResponse<List<Pending>> pending(@RequestParam(defaultValue = "") String after,
            @RequestAttribute("authorizedTenantId") UUID tenant,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requireCreate(role);
        return ApiResponse.success(imports.pending(tenant, after));
    }

    @PostMapping("/pending/complete")
    public ApiResponse<UUID> complete(@RequestBody Complete input,
            @RequestAttribute("authorizedTenantId") UUID tenant, @RequestAttribute("authorizedUserId") UUID user,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requireCreate(role);
        if (input.externalOrderCode() == null || input.externalOrderCode().isBlank() || input.address() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return ApiResponse.success(imports.complete(tenant, user, role, input.externalOrderCode(), input.address()));
    }

    @PostMapping("/pending/cancel")
    public ApiResponse<Boolean> cancel(@RequestBody Cancel input,
            @RequestAttribute("authorizedTenantId") UUID tenant,
            @RequestAttribute(name = "effectiveRole", required = false) String role) {
        requireCreate(role);
        if (input.externalOrderCode() == null || input.externalOrderCode().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return ApiResponse.success(imports.cancel(tenant, input.externalOrderCode()));
    }

    private void requireCreate(String role) {
        if (!TenantRequestInterceptor.isAuthorizedForPermission(role, "order:create",
                Set.of("OWNER", "TENANT_ADMIN", "SYSTEM_ADMIN", "OPERATOR", "USER"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
