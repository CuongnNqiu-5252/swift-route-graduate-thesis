package com.swiftroute.driverservice.controller;

import com.swiftroute.driverservice.dto.response.PageResponse;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.driverservice.dto.request.RegisterDriverRequest;
import com.swiftroute.driverservice.dto.request.UpdateAvailabilityRequest;
import com.swiftroute.driverservice.dto.request.UpdateDriverRequest;
import com.swiftroute.driverservice.dto.request.UpdateLocationRequest;
import com.swiftroute.driverservice.dto.response.ApiResponse;
import com.swiftroute.driverservice.dto.response.DriverResponse;
import com.swiftroute.driverservice.service.DriverService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Driver Management", description = "Endpoints for driver profiles, status, GPS location, and fleet administration")
public class DriverController {

    private final DriverService driverService;

    public record ReviewProfileRequest(boolean approved,
            @jakarta.validation.constraints.Size(max = 1000) String note) {
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> completeMyProfile(
            @Valid @RequestBody UpdateDriverRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(driverService.completeMyProfile(resolveUserId(authentication), request)));
    }

    @PostMapping("/me/submit")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> submitMyProfile(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(driverService.submitMyProfile(resolveUserId(authentication))));
    }

    @PostMapping("/{driverId}/review")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OWNER')")
    public ResponseEntity<ApiResponse<DriverResponse>> reviewProfile(@PathVariable UUID driverId,
            @Valid @RequestBody ReviewProfileRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                driverService.reviewProfile(driverId, request.approved(), request.note(), resolveUserId(authentication),
                        resolveRole(authentication))));
    }

    /**
     * GET /api/drivers
     * List all drivers (scoped to authorized tenant, or filtered by tenant_id for SYSTEM_ADMIN / internal calls).
     * Used by Web Admin and Dispatch console.
     */
    @GetMapping("/page")
    public ApiResponse<PageResponse<DriverResponse>> getDriverPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantIdParam,
            Authentication authentication) {
        UUID tenant = tenantId != null ? tenantId : tenantIdParam;
        return ApiResponse.success(driverService.getDriverPage(tenant, resolveRole(authentication), page, size));
    }

    @Operation(summary = "List all drivers (or filter by tenant)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DriverResponse>>> getAllDrivers(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID authorizedTenantId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantIdParam,
            Authentication authentication) {

        UUID tenantUuid = authorizedTenantId != null ? authorizedTenantId : tenantIdParam;
        List<DriverResponse> responses = driverService.getAllDrivers(tenantUuid, resolveRole(authentication));
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * POST /api/drivers
     * Register a new driver under a tenant.
     * Roles: SYSTEM_ADMIN, TENANT_ADMIN, OPERATOR, OWNER
     */
    @Operation(summary = "Register a new driver for a tenant")
    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN', 'OPERATOR', 'OWNER')")
    public ResponseEntity<ApiResponse<DriverResponse>> registerDriver(
            @Valid @RequestBody RegisterDriverRequest request,
            Authentication authentication) {

        DriverResponse response = driverService.registerDriver(request, resolveUserId(authentication), resolveRole(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * GET /api/drivers/me
     * Fetch current authenticated driver's profile.
     * Role: DRIVER
     */
    @Operation(summary = "Get current driver profile")
    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> getMyProfile(Authentication authentication) {

        DriverResponse response = driverService.getMyProfile(resolveUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/drivers/me/availability
     * Toggle current authenticated driver's online/offline availability.
     * Role: DRIVER
     */
    @Operation(summary = "Toggle current driver availability (online/offline)")
    @org.springframework.web.bind.annotation.PatchMapping("/me/availability")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateMyAvailability(
            @Valid @RequestBody UpdateAvailabilityRequest request,
            Authentication authentication) {

        DriverResponse response = driverService.updateMyAvailability(resolveUserId(authentication), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/drivers/me/location
     * Update current authenticated driver's real-time GPS coordinates (POST alias
     * for background sync).
     * Role: DRIVER
     */
    @Operation(summary = "Update current driver GPS coordinates (POST)")
    @PostMapping("/me/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateMyLocationPost(
            @Valid @RequestBody UpdateLocationRequest request,
            Authentication authentication) {

        DriverResponse response = driverService.updateMyLocation(resolveUserId(authentication), request);
        return ResponseEntity.ok(ApiResponse.success(response));
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
