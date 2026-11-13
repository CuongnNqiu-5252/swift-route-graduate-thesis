package com.swiftroute.orderservice.controller;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.CheckDeliveryZoneRequest;
import com.swiftroute.orderservice.dto.request.UpsertDeliveryZoneRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.DeliveryZoneCheckResponse;
import com.swiftroute.orderservice.dto.response.DeliveryZoneResponse;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.security.TenantContextValidator;
import com.swiftroute.orderservice.service.DeliveryZoneService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({ "/api/tenants", "/api/v1/tenants" })
@RequiredArgsConstructor
@Tag(name = "Tenant Delivery Zones", description = "Configure and validate geographic delivery boundaries per tenant")
public class DeliveryZoneController {

    private final DeliveryZoneService deliveryZoneService;
    private final TenantContextValidator tenantContextValidator;

    @Operation(summary = "Lấy cấu hình vùng giao hàng của doanh nghiệp hiện tại")
    @GetMapping("/me/delivery-zone")
    public ResponseEntity<ApiResponse<DeliveryZoneResponse>> getMyZone(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceAccess(tenantIdHeader, authHeader);
        return ResponseEntity.ok(ApiResponse.success(deliveryZoneService.getZone(tenant)));
    }

    @Operation(summary = "Cập nhật cấu hình vùng giao hàng cho doanh nghiệp hiện tại")
    @PutMapping("/me/delivery-zone")
    public ResponseEntity<ApiResponse<DeliveryZoneResponse>> upsertMyZone(
            @Valid @RequestBody UpsertDeliveryZoneRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceOwner(tenantIdHeader, authHeader);
        UUID actorId = tenantContextValidator.extractUserId(authHeader).orElse(tenant.getOwnerUserId());
        return ResponseEntity.ok(ApiResponse.success(deliveryZoneService.upsertZone(tenant, request, actorId)));
    }

    @Operation(summary = "Kiểm tra một tọa độ giao hàng có hợp lệ với vùng phục vụ của doanh nghiệp hiện tại")
    @PostMapping("/me/delivery-zone/check")
    public ResponseEntity<ApiResponse<DeliveryZoneCheckResponse>> checkMyLocation(
            @Valid @RequestBody CheckDeliveryZoneRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceAccess(tenantIdHeader, authHeader);
        return ResponseEntity.ok(ApiResponse.success(
                deliveryZoneService.checkLocation(tenant.getId(), request.lat(), request.lng())));
    }
}
