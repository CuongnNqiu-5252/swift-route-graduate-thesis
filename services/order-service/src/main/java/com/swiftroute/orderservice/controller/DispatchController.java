package com.swiftroute.orderservice.controller;

import org.springframework.web.bind.annotation.RequestParam;
import com.swiftroute.orderservice.dto.response.PageRequests;
import com.swiftroute.orderservice.dto.response.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftroute.orderservice.dto.request.ApprovePlanRequest;
import com.swiftroute.orderservice.dto.request.CancelPlanRequest;
import com.swiftroute.orderservice.dto.request.StartSimulationRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.DispatchPlanResponse;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.service.TrackingSimulationService;
import com.swiftroute.orderservice.service.planning.DispatchOrchestrationService;
import com.swiftroute.orderservice.service.planning.PlanningTriggerScheduler;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API for dispatch plan lifecycle management.
 *
 * <p>All endpoints require the caller to have MANAGER or ADMIN role
 * (enforced by the API Gateway filter).
 *
 * <p>Base path: {@code /api/orders/dispatch}
 */
@RestController
@RequestMapping("/api/orders/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchPlanRepository dispatchPlanRepository;
    private final DispatchOrchestrationService orchestrationService;
    private final PlanningTriggerScheduler planningTriggerScheduler;
    private final TrackingSimulationService trackingSimulationService;

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @GetMapping("/plans")
    @Operation(summary = "List dispatch plans for the calling tenant")
    public ResponseEntity<ApiResponse<List<DispatchPlanResponse>>> listPlans(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        List<DispatchPlanResponse> result = dispatchPlanRepository
                .findAllByTenantIdOrderByCreatedAtDesc(requireTenantId(tenantId))
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/plans/page")
    public ApiResponse<PageResponse<DispatchPlanResponse>> listPlanPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        return ApiResponse.success(PageResponse.fromPage(dispatchPlanRepository.findByTenantId(requireTenantId(tenantId),
                PageRequests.newestFirst(page, size, "createdAt")).map(this::toResponse)));
    }

    @GetMapping("/driver-plans")
    @Operation(summary = "List active DISPATCHED plans assigned to the calling driver")
    public ResponseEntity<ApiResponse<List<DispatchPlanResponse>>> listDriverPlans(
            @RequestAttribute("authorizedUserId") UUID driverId,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        List<DispatchPlanResponse> result = dispatchPlanRepository
                .findAllByTenantIdAndDriverIdAndPlanStatusIn(
                        requireTenantId(tenantId), driverId, List.of(PlanStatus.DISPATCHED))
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    @PostMapping("/trigger")
    @Operation(summary = "Manually trigger a planning cycle for the calling tenant")
    public ResponseEntity<ApiResponse<String>> triggerManual(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        planningTriggerScheduler.triggerManual(requireTenantId(tenantId));
        return ResponseEntity.accepted().body(ApiResponse.success("Planning cycle triggered."));
    }

    @PostMapping("/plans/{planId}/approve")
    @Operation(summary = "Approve a DRAFT dispatch plan — requires MANAGER or ADMIN role")
    public ResponseEntity<ApiResponse<DispatchPlanResponse>> approvePlan(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @PathVariable UUID planId,
            @Valid @RequestBody ApprovePlanRequest request) {
        findPlanInTenant(planId, requireTenantId(tenantId));
        DispatchPlan plan = orchestrationService.approvePlan(planId, userId, request.driverId());
        return ResponseEntity.ok(ApiResponse.success(toResponse(plan)));
    }

    @PostMapping("/plans/{planId}/dispatch")
    @Operation(summary = "Dispatch an APPROVED plan to the assigned driver")
    public ResponseEntity<ApiResponse<DispatchPlanResponse>> dispatchPlan(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @PathVariable UUID planId) {
        DispatchPlan plan = findPlanInTenant(planId, requireTenantId(tenantId));
        plan = orchestrationService.dispatchPlan(plan);
        return ResponseEntity.ok(ApiResponse.success(toResponse(plan)));
    }

    @PostMapping("/plans/{planId}/cancel")
    @Operation(summary = "Cancel a dispatch plan (DRAFT, APPROVED, or DISPATCHED)")
    public ResponseEntity<ApiResponse<DispatchPlanResponse>> cancelPlan(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @PathVariable UUID planId,
            @Valid @RequestBody CancelPlanRequest request) {
        findPlanInTenant(planId, requireTenantId(tenantId));
        DispatchPlan plan = orchestrationService.cancelPlan(planId, request.reason());
        return ResponseEntity.ok(ApiResponse.success(toResponse(plan)));
    }

    @PostMapping("/plans/{planId}/acknowledge")
    @Operation(summary = "Driver acknowledges acceptance of the dispatched plan")
    public ResponseEntity<ApiResponse<DispatchPlanResponse>> acknowledgePlan(
            @PathVariable UUID planId,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role) {
        DispatchPlan plan = findPlanInTenant(planId, requireTenantId(tenantId));
        authorizeDriverPlanAction(plan, userId, role);
        plan = orchestrationService.acknowledgeByDriver(planId);
        return ResponseEntity.ok(ApiResponse.success(toResponse(plan)));
    }

    @PostMapping("/plans/{planId}/decline")
    @Operation(summary = "Driver declines the dispatched plan")
    public ResponseEntity<ApiResponse<DispatchPlanResponse>> declinePlan(
            @PathVariable UUID planId,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String role,
            @Valid @RequestBody CancelPlanRequest request) {
        DispatchPlan plan = findPlanInTenant(planId, requireTenantId(tenantId));
        authorizeDriverPlanAction(plan, userId, role);
        plan = orchestrationService.declineByDriver(planId, request.reason());
        return ResponseEntity.ok(ApiResponse.success(toResponse(plan)));
    }

    @GetMapping(value = "/plans/{planId}/tracking/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time SSE GPS tracking stream for a dispatch plan")
    public SseEmitter streamPlanTracking(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @PathVariable UUID planId) {
        findPlanInTenant(planId, requireTenantId(tenantId));
        return trackingSimulationService.subscribe(planId);
    }

    @PostMapping("/plans/{planId}/tracking/simulate")
    @Operation(summary = "Start on-demand GPS route simulation for a dispatch plan and its orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startPlanSimulation(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @PathVariable UUID planId,
            @Valid @RequestBody StartSimulationRequest request) {
        DispatchPlan plan = findPlanInTenant(planId, requireTenantId(tenantId));
        List<UUID> orderIds = plan.getPlanOrders().stream()
                .map(po -> po.getOrder().getId())
                .toList();
        trackingSimulationService.startRouteSimulation(
                planId,
                plan.getDriverId(),
                orderIds,
                request.waypoints(),
                request.intervalMs());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "plan_id", planId,
                "waypoint_count", request.waypoints().size(),
                "status", "SIMULATING")));
    }

    @PostMapping("/plans/{planId}/tracking/simulate/stop")
    @Operation(summary = "Stop on-demand GPS route simulation for a dispatch plan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopPlanSimulation(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @PathVariable UUID planId) {
        DispatchPlan plan = findPlanInTenant(planId, requireTenantId(tenantId));
        List<UUID> orderIds = plan.getPlanOrders().stream()
                .map(po -> po.getOrder().getId())
                .toList();
        trackingSimulationService.stopRouteSimulation(planId, orderIds);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "plan_id", planId,
                "status", "STOPPED")));
    }

    @PostMapping("/plans/{planId}/insert/{orderId}")
    @Operation(summary = "Dynamically insert a new order into an active DISPATCHED plan using OR-Tools")
    public ResponseEntity<ApiResponse<DispatchPlanResponse>> insertOrderIntoPlan(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @PathVariable UUID planId,
            @PathVariable UUID orderId) {
        findPlanInTenant(planId, requireTenantId(tenantId));
        DispatchPlan plan = orchestrationService.insertOrderIntoActivePlan(planId, orderId, null, null);
        return ResponseEntity.ok(ApiResponse.success(toResponse(plan)));
    }

    @PostMapping("/plans/{planId}/reoptimize")
    @Operation(summary = "Safely reoptimize remaining uncompleted stops of an active DISPATCHED plan")
    public ResponseEntity<ApiResponse<DispatchPlanResponse>> reoptimizePlan(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @PathVariable UUID planId) {
        findPlanInTenant(planId, requireTenantId(tenantId));
        DispatchPlan plan = orchestrationService.reoptimizeActivePlan(
                planId, "MANUAL_REOPTIMIZE", null, null);
        return ResponseEntity.ok(ApiResponse.success(toResponse(plan)));
    }

    private UUID requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return tenantId;
    }

    private DispatchPlan findPlanInTenant(UUID planId, UUID tenantId) {
        return dispatchPlanRepository.findById(planId)
                .filter(p -> p.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
    }

    private void authorizeDriverPlanAction(DispatchPlan plan, UUID userId, String role) {
        if (role == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        String normalizedRole = role.toUpperCase(Locale.ROOT);
        if (!Set.of("DRIVER", "OWNER", "SYSTEM_ADMIN").contains(normalizedRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if ("DRIVER".equals(normalizedRole)) {
            if (plan.getDriverId() == null || !plan.getDriverId().equals(userId)) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mapper
    // -------------------------------------------------------------------------

    private DispatchPlanResponse toResponse(DispatchPlan plan) {
        List<UUID> stopIds = plan.getPlanOrders().stream()
                .sorted((a, b) -> Integer.compare(a.getStopIndex(), b.getStopIndex()))
                .map(po -> po.getOrder().getId())
                .toList();
        return new DispatchPlanResponse(
                plan.getId(),
                plan.getTenant().getId(),
                plan.getPlanStatus().name(),
                plan.getDriverId(),
                plan.getAlgorithmUsed(),
                plan.getTotalDistanceKm(),
                plan.getEstimatedDurationMinutes(),
                plan.getTriggerType() != null ? plan.getTriggerType().name() : null,
                plan.getRequiresApproval(),
                plan.getPlanOrders().size(),
                stopIds,
                plan.getPlannedAt(),
                plan.getApprovedAt(),
                plan.getDispatchedAt(),
                plan.getCancelledAt(),
                plan.getCancelReason(),
                plan.getAckDeadlineAt(),
                plan.getAcknowledgedAt(),
                plan.getRouteWaypoints(),
                plan.getCreatedAt());
    }
}
