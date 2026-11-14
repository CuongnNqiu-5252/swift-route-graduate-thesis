package com.swiftroute.orderservice.controller;

import com.swiftroute.orderservice.dto.response.PageResponse;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.CreatePickupLocationRequest;
import com.swiftroute.orderservice.dto.request.UpdatePickupLocationRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.PickupLocationResponse;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.PickupLocationStatus;
import com.swiftroute.orderservice.security.TenantContextValidator;
import com.swiftroute.orderservice.service.PickupLocationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pickup-locations")
@RequiredArgsConstructor
@Tag(name = "Pickup Locations", description = "Tenant-scoped multi-pickup location and warehouse management")
public class PickupLocationController {

    private final PickupLocationService pickupLocationService;
    private final TenantContextValidator tenantContextValidator;

    @Operation(summary = "Lấy danh sách các điểm lấy hàng / kho của doanh nghiệp")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PickupLocationResponse>>> getPickupLocations(
            @RequestParam(required = false) PickupLocationStatus status,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceAccess(tenantIdHeader, authHeader);
        List<PickupLocationResponse> response = pickupLocationService.getPickupLocations(tenant.getId(), status);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/page")
    public ApiResponse<PageResponse<PickupLocationResponse>> getPickupLocationPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) PickupLocationStatus status,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        Tenant tenant = tenantContextValidator.validateWorkspaceAccess(tenantIdHeader, authHeader);
        return ApiResponse.success(pickupLocationService.getPickupLocationPage(tenant.getId(), status, page, size));
    }

    @Operation(summary = "Lấy chi tiết một điểm lấy hàng theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PickupLocationResponse>> getPickupLocation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceAccess(tenantIdHeader, authHeader);
        PickupLocationResponse response = pickupLocationService.getPickupLocation(tenant.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Tạo mới một điểm lấy hàng cho doanh nghiệp")
    @PostMapping
    public ResponseEntity<ApiResponse<PickupLocationResponse>> createPickupLocation(
            @Valid @RequestBody CreatePickupLocationRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceOwner(tenantIdHeader, authHeader);
        PickupLocationResponse response = pickupLocationService.createPickupLocation(tenant.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật thông tin điểm lấy hàng")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PickupLocationResponse>> updatePickupLocation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePickupLocationRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceOwner(tenantIdHeader, authHeader);
        PickupLocationResponse response = pickupLocationService.updatePickupLocation(tenant.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Thiết lập điểm lấy hàng làm mặc định")
    @PatchMapping("/{id}/set-default")
    public ResponseEntity<ApiResponse<PickupLocationResponse>> setDefaultLocation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceOwner(tenantIdHeader, authHeader);
        PickupLocationResponse response = pickupLocationService.setDefaultLocation(tenant.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Ngừng hoạt động (soft-delete) điểm lấy hàng")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePickupLocation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceOwner(tenantIdHeader, authHeader);
        pickupLocationService.deletePickupLocation(tenant.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

