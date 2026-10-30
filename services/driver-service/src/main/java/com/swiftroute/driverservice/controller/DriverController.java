package com.swiftroute.driverservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import com.swiftroute.driverservice.dto.request.RegisterDriverRequest;
import com.swiftroute.driverservice.dto.request.ReportDeliveryEventRequest;
import com.swiftroute.driverservice.dto.request.UpdateAvailabilityRequest;
import com.swiftroute.driverservice.dto.request.UpdateDriverRequest;
import com.swiftroute.driverservice.dto.request.UpdateLocationRequest;
import com.swiftroute.driverservice.dto.response.ApiResponse;
import com.swiftroute.driverservice.dto.response.DriverPublicResponse;
import com.swiftroute.driverservice.dto.response.DriverResponse;
import com.swiftroute.driverservice.entity.enums.DriverStatus;
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

    /**
     * GET /api/drivers
     * List all drivers (or filtered by tenant).
     * Used by Web Admin and Dispatch console.
     */
    @Operation(summary = "List all drivers (or filter by tenant)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DriverResponse>>> getAllDrivers(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestParam(value = "tenant_id", required = false) String tenantIdParam,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "ADMIN") String userRole) {

        String effectiveTenant = (tenantIdHeader != null && !tenantIdHeader.isBlank())
                ? tenantIdHeader
                : tenantIdParam;

        UUID tenantUuid = (effectiveTenant != null && !effectiveTenant.isBlank())
                ? UUID.fromString(effectiveTenant)
                : null;

        List<DriverResponse> responses = driverService.getAllDrivers(tenantUuid, userRole);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * POST /api/drivers
     * Register a new driver under a tenant.
     * Roles: ADMIN, OPS_STAFF, OWNER
     */
    @Operation(summary = "Register a new driver for a tenant")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<DriverResponse>> registerDriver(
            @Valid @RequestBody RegisterDriverRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        DriverResponse response = driverService.registerDriver(request, UUID.fromString(userId), userRole);
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
    public ResponseEntity<ApiResponse<DriverResponse>> getMyProfile(
            @RequestHeader("X-User-Id") String userId) {

        DriverResponse response = driverService.getMyProfile(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/drivers/me/availability
     * Toggle current authenticated driver's online/offline availability.
     * Role: DRIVER
     */
    @Operation(summary = "Toggle current driver availability (online/offline)")
    @PatchMapping("/me/availability")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateMyAvailability(
            @Valid @RequestBody UpdateAvailabilityRequest request,
            @RequestHeader("X-User-Id") String userId) {

        DriverResponse response = driverService.updateMyAvailability(UUID.fromString(userId), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/drivers/me/location
     * Update current authenticated driver's real-time GPS coordinates.
     * Role: DRIVER
     */
    @Operation(summary = "Update current driver GPS coordinates (PATCH)")
    @PatchMapping("/me/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateMyLocationPatch(
            @Valid @RequestBody UpdateLocationRequest request,
            @RequestHeader("X-User-Id") String userId) {

        DriverResponse response = driverService.updateMyLocation(UUID.fromString(userId), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/drivers/me/location
     * Update current authenticated driver's real-time GPS coordinates (POST alias for background sync).
     * Role: DRIVER
     */
    @Operation(summary = "Update current driver GPS coordinates (POST)")
    @PostMapping("/me/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateMyLocationPost(
            @Valid @RequestBody UpdateLocationRequest request,
            @RequestHeader("X-User-Id") String userId) {

        DriverResponse response = driverService.updateMyLocation(UUID.fromString(userId), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/drivers/me/delivery-events
     * Driver reports delivery progress event (STARTED, DELIVERED, FAILED).
     * Role: DRIVER
     */
    @Operation(summary = "Report delivery progress event from driver")
    @PostMapping("/me/delivery-events")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<Void>> reportDeliveryEvent(
            @Valid @RequestBody ReportDeliveryEventRequest request,
            @RequestHeader("X-User-Id") String userId) {

        driverService.reportDeliveryEvent(UUID.fromString(userId), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(null));
    }

    /**
     * GET /api/drivers/{driverId}/public
     * Get basic public profile (name, phone, license plate, coordinates) for customer tracking.
     */
    @Operation(summary = "Get basic public driver info for order tracking")
    @GetMapping("/{driverId}/public")
    public ResponseEntity<ApiResponse<DriverPublicResponse>> getDriverPublicInfo(
            @PathVariable UUID driverId) {

        DriverPublicResponse response = driverService.getDriverPublicInfo(driverId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/drivers/{driverId}
     * Get full driver details by driver ID.
     */
    @Operation(summary = "Get full driver details by ID")
    @GetMapping("/{driverId}")
    public ResponseEntity<ApiResponse<DriverResponse>> getDriverById(
            @PathVariable UUID driverId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        UUID requesterTenantId = (tenantId != null && !tenantId.isBlank()) ? UUID.fromString(tenantId) : null;
        DriverResponse response = driverService.getDriverById(driverId, UUID.fromString(userId), userRole, requesterTenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/drivers/tenant/{tenantId}
     * List all drivers belonging to a tenant.
     * Roles: ADMIN, OPS_STAFF, OWNER
     */
    @Operation(summary = "List all drivers in a tenant")
    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<List<DriverResponse>>> getDriversByTenant(
            @PathVariable UUID tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        List<DriverResponse> responses = driverService.getDriversByTenant(tenantId, UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * GET /api/drivers/tenant/{tenantId}/available
     * List active and available drivers in a tenant for route dispatch.
     */
    @Operation(summary = "List available drivers in a tenant")
    @GetMapping("/tenant/{tenantId}/available")
    public ResponseEntity<ApiResponse<List<DriverResponse>>> getAvailableDriversByTenant(
            @PathVariable UUID tenantId) {

        List<DriverResponse> responses = driverService.getAvailableDriversByTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * PUT /api/drivers/{driverId}
     * Update driver details (admin / tenant operator).
     */
    @Operation(summary = "Update driver details")
    @PutMapping("/{driverId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateDriver(
            @PathVariable UUID driverId,
            @Valid @RequestBody UpdateDriverRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        DriverResponse response = driverService.updateDriver(driverId, request, UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/drivers/{driverId}/location
     * Update driver GPS coordinates by driverId.
     */
    @Operation(summary = "Update driver GPS coordinates by driverId")
    @PatchMapping("/{driverId}/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateLocation(
            @PathVariable UUID driverId,
            @Valid @RequestBody UpdateLocationRequest request,
            @RequestHeader("X-User-Id") String userId) {

        DriverResponse response = driverService.updateLocation(driverId, request, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/drivers/{driverId}/availability
     * Update driver availability by driverId.
     */
    @Operation(summary = "Update driver availability by driverId")
    @PatchMapping("/{driverId}/availability")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateAvailability(
            @PathVariable UUID driverId,
            @Valid @RequestBody UpdateAvailabilityRequest request,
            @RequestHeader("X-User-Id") String userId) {

        DriverResponse response = driverService.updateAvailability(driverId, request, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/drivers/{driverId}/status
     * Change driver operational status (ACTIVE, SUSPENDED, INACTIVE).
     */
    @Operation(summary = "Change driver operational status")
    @PatchMapping("/{driverId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS_STAFF')")
    public ResponseEntity<ApiResponse<DriverResponse>> changeStatus(
            @PathVariable UUID driverId,
            @RequestBody DriverStatus newStatus,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        DriverResponse response = driverService.changeStatus(driverId, newStatus, UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
