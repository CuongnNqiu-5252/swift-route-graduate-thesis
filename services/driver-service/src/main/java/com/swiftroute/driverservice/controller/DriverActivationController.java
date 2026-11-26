package com.swiftroute.driverservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.driverservice.dto.request.BulkDriverActivationRequest;
import com.swiftroute.driverservice.dto.request.SendActivationEmailRequest;
import com.swiftroute.driverservice.dto.request.UpdateDriverDeviceRequest;
import com.swiftroute.driverservice.dto.response.ApiResponse;
import com.swiftroute.driverservice.dto.response.DriverActivationKeyResponse;
import com.swiftroute.driverservice.dto.response.DriverActivationStatusResponse;
import com.swiftroute.driverservice.dto.response.PageResponse;
import com.swiftroute.driverservice.service.DriverActivationManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Driver App Activation", description = "Endpoints for Driver App Activation codes, status, and registered devices")
public class DriverActivationController {

    private final DriverActivationManagementService activationService;

    @Operation(summary = "List tenant drivers with mobile app activation status and registered device details")
    @GetMapping("/activation-status")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OPERATOR', 'VIEWER', 'OWNER')")
    public ResponseEntity<ApiResponse<PageResponse<DriverActivationStatusResponse>>> getActivationStatus(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID authorizedTenantId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantIdParam,
            Authentication authentication) {
        UUID tenantId = authorizedTenantId != null ? authorizedTenantId : tenantIdParam;
        PageResponse<DriverActivationStatusResponse> result = activationService.getActivationStatusPage(
                tenantId,
                resolveRole(authentication),
                query,
                status,
                page,
                size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Generate a new 12-digit Driver App activation code")
    @PostMapping("/{driverId}/activation")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OPERATOR', 'OWNER')")
    public ResponseEntity<ApiResponse<DriverActivationKeyResponse>> generateActivationCode(
            @PathVariable UUID driverId,
            Authentication authentication) {
        DriverActivationKeyResponse result = activationService.generateActivationKey(
                driverId,
                false,
                null,
                resolveUserId(authentication),
                resolveRole(authentication));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Revoke any previous unused activation code and generate a new 12-digit code")
    @PostMapping("/{driverId}/activation/regenerate")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OPERATOR', 'OWNER')")
    public ResponseEntity<ApiResponse<DriverActivationKeyResponse>> regenerateActivationCode(
            @PathVariable UUID driverId,
            Authentication authentication) {
        DriverActivationKeyResponse result = activationService.generateActivationKey(
                driverId,
                false,
                null,
                resolveUserId(authentication),
                resolveRole(authentication));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Deactivate a driver's registered mobile device and revoke pending activation keys")
    @DeleteMapping("/{driverId}/activation")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OPERATOR', 'OWNER')")
    public ResponseEntity<ApiResponse<Void>> deactivateDriverApp(
            @PathVariable UUID driverId,
            Authentication authentication) {
        activationService.deactivateDriver(
                driverId,
                resolveUserId(authentication),
                resolveRole(authentication));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Generate and send a 12-digit activation code to the driver's email address")
    @PostMapping("/{driverId}/activation/send-email")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OPERATOR', 'OWNER')")
    public ResponseEntity<ApiResponse<DriverActivationKeyResponse>> sendActivationEmail(
            @PathVariable UUID driverId,
            @Valid @RequestBody(required = false) SendActivationEmailRequest request,
            Authentication authentication) {
        String overrideEmail = request != null ? request.email() : null;
        DriverActivationKeyResponse result = activationService.generateActivationKey(
                driverId,
                true,
                overrideEmail,
                resolveUserId(authentication),
                resolveRole(authentication));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Generate activation codes in bulk for selected drivers")
    @PostMapping("/activation/bulk")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OPERATOR', 'OWNER')")
    public ResponseEntity<ApiResponse<List<DriverActivationKeyResponse>>> bulkActivateDrivers(
            @Valid @RequestBody BulkDriverActivationRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID authorizedTenantId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantIdParam,
            Authentication authentication) {
        UUID tenantId = authorizedTenantId != null ? authorizedTenantId : tenantIdParam;
        List<DriverActivationKeyResponse> result = activationService.bulkGenerateActivationKeys(
                tenantId,
                request.driverIds(),
                request.sendEmail(),
                resolveUserId(authentication),
                resolveRole(authentication));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Generate and send activation codes via email in bulk for selected drivers")
    @PostMapping("/activation/bulk-send-email")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OPERATOR', 'OWNER')")
    public ResponseEntity<ApiResponse<List<DriverActivationKeyResponse>>> bulkSendActivationEmail(
            @Valid @RequestBody BulkDriverActivationRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID authorizedTenantId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantIdParam,
            Authentication authentication) {
        UUID tenantId = authorizedTenantId != null ? authorizedTenantId : tenantIdParam;
        List<DriverActivationKeyResponse> result = activationService.bulkGenerateActivationKeys(
                tenantId,
                request.driverIds(),
                true,
                resolveUserId(authentication),
                resolveRole(authentication));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Deactivate registered devices and pending keys in bulk for selected drivers")
    @PostMapping("/activation/bulk-deactivate")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OPERATOR', 'OWNER')")
    public ResponseEntity<ApiResponse<Void>> bulkDeactivateDrivers(
            @Valid @RequestBody BulkDriverActivationRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID authorizedTenantId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantIdParam,
            Authentication authentication) {
        UUID tenantId = authorizedTenantId != null ? authorizedTenantId : tenantIdParam;
        activationService.bulkDeactivateDrivers(
                tenantId,
                request.driverIds(),
                resolveUserId(authentication),
                resolveRole(authentication));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Update device metadata, installed app version, and last activity timestamp for current driver")
    @PatchMapping("/me/device")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<Void>> updateMyDevice(
            @Valid @RequestBody UpdateDriverDeviceRequest request,
            @RequestAttribute(name = "authorizedSessionId", required = false) UUID sessionId,
            Authentication authentication) {
        activationService.updateMyDevice(resolveUserId(authentication), sessionId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private UUID resolveUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private String resolveRole(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        boolean isSystemAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SYSTEM_ADMIN"::equals);
        if (isSystemAdmin) {
            return "SYSTEM_ADMIN";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .findFirst()
                .orElse(null);
    }
}
