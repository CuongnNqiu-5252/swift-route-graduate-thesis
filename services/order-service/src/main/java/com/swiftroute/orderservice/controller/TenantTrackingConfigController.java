package com.swiftroute.orderservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.TenantTrackingConfigRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.TenantTrackingConfigResponse;
import com.swiftroute.orderservice.service.TenantTrackingConfigService;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping({ "/api/tenant-tracking-config" })
public class TenantTrackingConfigController {
    private final TenantTrackingConfigService tenantTrackingConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<TenantTrackingConfigResponse>> getConfig(
            @RequestAttribute("authorizedTenantId") UUID tenantId) {
        return ResponseEntity.ok(
                ApiResponse.success(tenantTrackingConfigService.getConfig(tenantId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<TenantTrackingConfigResponse>> updateConfig(
            @RequestAttribute("authorizedTenantId") UUID tenantId,
            @Valid @RequestBody TenantTrackingConfigRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        tenantTrackingConfigService.updateConfig(tenantId, request)));
    }
}
