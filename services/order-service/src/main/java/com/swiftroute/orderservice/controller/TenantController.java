package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.swiftroute.orderservice.dto.request.ApplyPresetRequest;
import com.swiftroute.orderservice.dto.request.CreateTenantRequest;
import com.swiftroute.orderservice.dto.request.TenantStatusRequest;
import com.swiftroute.orderservice.dto.request.UpdateTenantSettingsRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.TenantResponse;
import com.swiftroute.orderservice.dto.response.TenantSettingsResponse;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.security.TenantContextValidator;
import com.swiftroute.orderservice.service.TenantService;
import com.swiftroute.orderservice.service.TenantSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Tenant onboarding, business settings, and platform-review endpoints. */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "Tenant lifecycle, business settings, and dispatch profiles")
public class TenantController {

    private final TenantService tenantService;
    private final TenantSettingsService tenantSettingsService;
    private final TenantContextValidator tenantContextValidator;

    @Operation(summary = "Get approved tenants near a given location")
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<TenantResponse>>> getNearbyTenants(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        List<TenantResponse> responses = tenantService.getNearbyTenants(lat, lng, limit);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "Get business settings for authenticated tenant")
    @GetMapping("/me/settings")
    public ResponseEntity<ApiResponse<TenantSettingsResponse>> getMySettings(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceAccess(tenantIdHeader, authHeader);
        TenantSettingsResponse response = tenantSettingsService.getSettings(tenant.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Update business settings for authenticated tenant")
    @PutMapping("/me/settings")
    public ResponseEntity<ApiResponse<TenantSettingsResponse>> updateMySettings(
            @Valid @RequestBody UpdateTenantSettingsRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceOwner(tenantIdHeader, authHeader);
        TenantSettingsResponse response = tenantSettingsService.updateSettings(tenant.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Apply recommended business profile preset for authenticated tenant")
    @PostMapping("/me/settings/apply-preset")
    public ResponseEntity<ApiResponse<TenantSettingsResponse>> applyPreset(
            @Valid @RequestBody ApplyPresetRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Tenant tenant = tenantContextValidator.validateWorkspaceOwner(tenantIdHeader, authHeader);
        TenantSettingsResponse response = tenantSettingsService.applyPreset(tenant.getId(), request.businessType());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get automated dispatch configuration for a tenant")
    @GetMapping("/{tenantId}/dispatch-config")
    public ResponseEntity<ApiResponse<TenantDispatchConfig>> getDispatchConfig(
            @PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.getDispatchConfig(tenantId)));
    }

    @Operation(summary = "Update automated dispatch configuration for a tenant")
    @PutMapping("/{tenantId}/dispatch-config")
    public ResponseEntity<ApiResponse<TenantDispatchConfig>> updateDispatchConfig(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantDispatchConfig config,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(ApiResponse.success(
                tenantService.updateDispatchConfig(tenantId, config, UUID.fromString(userId), userRole)));
    }
}
