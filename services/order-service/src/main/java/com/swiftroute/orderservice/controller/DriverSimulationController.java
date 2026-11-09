package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.swiftroute.orderservice.dto.request.StartSimulationRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.service.TrackingSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders/dispatch/drivers/{driverId}/tracking")
public class DriverSimulationController {
    private final TrackingSimulationService tracking;
    private final RestClient fleet;
    private final String key;

    public DriverSimulationController(TrackingSimulationService tracking,
            @Value("${driver-service.url}") String url,
            @Value("${tenant.internal-key}") String key) {
        this.tracking = tracking;
        this.fleet = RestClient.create(url);
        this.key = key;
    }

    private UUID authorize(UUID tenantId, UUID driverId) {
        var body = fleet.get().uri("/internal/fleet/{tenant}/simulation-profile/{driver}", tenantId, driverId)
                .header("X-Service-Key", key).retrieve().body(Map.class);
        if (body == null || body.get("tracking_id") == null) throw new IllegalStateException("Missing tracking identity");
        return UUID.fromString(body.get("tracking_id").toString());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Track any driver belonging to the current tenant")
    public SseEmitter stream(@RequestHeader("X-Tenant-Id") UUID tenant, @PathVariable UUID driverId) {
        return tracking.subscribe(authorize(tenant, driverId));
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simulate a tenant driver, including drivers without assigned orders")
    public ApiResponse<Map<String, String>> start(@RequestHeader("X-Tenant-Id") UUID tenant,
            @PathVariable UUID driverId, @Valid @RequestBody StartSimulationRequest request) {
        UUID identity = authorize(tenant, driverId);
        tracking.startRouteSimulation(identity, identity, List.of(), request.waypoints(), request.intervalMs());
        tracking.pauseAt(identity, request.pauseAtIndex());
        return ApiResponse.success(Map.of("status", "SIMULATING"));
    }

    @PostMapping("/simulate/stop")
    @Operation(summary = "Stop simulation of a tenant driver")
    public ApiResponse<Map<String, String>> stop(@RequestHeader("X-Tenant-Id") UUID tenant, @PathVariable UUID driverId) {
        tracking.stopRouteSimulation(authorize(tenant, driverId), List.of());
        return ApiResponse.success(Map.of("status", "STOPPED"));
    }

    @PostMapping("/simulate/resume")
    @Operation(summary = "Resume simulated movement after waiting at pickup")
    public ApiResponse<Map<String, String>> resume(@RequestHeader("X-Tenant-Id") UUID tenant, @PathVariable UUID driverId) {
        tracking.resume(authorize(tenant, driverId));
        return ApiResponse.success(Map.of("status", "SIMULATING"));
    }
}
