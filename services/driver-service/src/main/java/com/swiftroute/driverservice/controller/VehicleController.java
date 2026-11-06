package com.swiftroute.driverservice.controller;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.driverservice.dto.request.*;
import com.swiftroute.driverservice.dto.response.*;
import com.swiftroute.driverservice.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/vehicles") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN','TENANT_ADMIN','OPS_STAFF','OWNER')")
public class VehicleController {
    private final VehicleService service;
    @GetMapping public ApiResponse<List<VehicleResponse>> list(@RequestHeader("X-Tenant-Id") UUID tenantId) { return ApiResponse.success(service.list(tenantId)); }
    @PostMapping public ResponseEntity<ApiResponse<VehicleResponse>> create(@Valid @RequestBody UpsertVehicleRequest r) { return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(r))); }
    @PutMapping("/{id}") public ApiResponse<VehicleResponse> update(@PathVariable UUID id, @Valid @RequestBody UpsertVehicleRequest r) { return ApiResponse.success(service.update(id, r)); }
    @PatchMapping("/{id}/assignment") public ApiResponse<VehicleResponse> assign(@PathVariable UUID id, @RequestBody AssignVehicleRequest r) { return ApiResponse.success(service.assign(id, r.driverId())); }
}
