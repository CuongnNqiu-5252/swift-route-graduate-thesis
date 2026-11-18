package com.swiftroute.orderservice.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftroute.orderservice.dto.request.ApprovePlanRequest;
import com.swiftroute.orderservice.dto.request.CancelPlanRequest;
import com.swiftroute.orderservice.dto.request.MovePlanStopRequest;
import com.swiftroute.orderservice.dto.request.ReorderPlanStopsRequest;
import com.swiftroute.orderservice.dto.request.StartSimulationRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.DispatchPlanResponse;
import com.swiftroute.orderservice.dto.response.PageRequests;
import com.swiftroute.orderservice.dto.response.PageResponse;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;
import com.swiftroute.orderservice.service.TrackingSimulationService;
import com.swiftroute.orderservice.service.planning.DispatchOrchestrationService;
import com.swiftroute.orderservice.service.planning.PlanningTriggerScheduler;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API for dispatch plan lifecycle management.
 *
 * <p>
 * All endpoints require the caller to have MANAGER or ADMIN role
 * (enforced by the API Gateway filter).
 *
 * <p>
 * Base path: {@code /api/orders/dispatch}
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
        return ApiResponse
                .success(PageResponse.fromPage(dispatchPlanRepository.findByTenantId(requireTenantId(tenantId),
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

    @PostMapping({ "/trigger" })
    @Operation(summary = "Create unassigned draft routes for eligible orders and return the planning result")
    public ResponseEntity<ApiResponse<ManualPlanningResult>> triggerManual(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        var cycle = planningTriggerScheduler.triggerManual(requireTenantId(tenantId));
        return ResponseEntity.ok(ApiResponse.success(new ManualPlanningResult(
                cycle.outcome(), cycle.plans().stream().map(DispatchPlan::getId).toList())));
    }

    public record ManualPlanningResult(String outcome, @JsonProperty("plan_ids") List<UUID> planIds) {
    }

    @PostMapping("/plans/{planId}/stop-order")
    @Operation(summary = "Reorder and measure stops on an unassigned draft plan")
    public ApiResponse<DispatchPlanResponse> reorderStops(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("effectiveRole") String role,
            @PathVariable UUID planId,
            @Valid @RequestBody ReorderPlanStopsRequest request) {
        if ("DRIVER".equalsIgnoreCase(role) || !TenantRequestInterceptor.isAuthorizedForPermission(
                role, "dispatch:execute", Set.of("TENANT_ADMIN", "OPERATOR"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return ApiResponse.success(toResponse(orchestrationService.reorderDraftStops(
                requireTenantId(tenantId), planId, request)));
    }

    @PostMapping("/plans/move-stop")
    @Operation(summary = "Move a stop between two unassigned draft plans and re-measure both routes")
    public ApiResponse<MoveStopResponse> moveStop(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("effectiveRole") String role,
            @Valid @RequestBody MovePlanStopRequest request) {
        if ("DRIVER".equalsIgnoreCase(role) || !TenantRequestInterceptor.isAuthorizedForPermission(
                role, "dispatch:execute", Set.of("TENANT_ADMIN", "OPERATOR"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        var result = orchestrationService.moveDraftStop(requireTenantId(tenantId), request);
        return ApiResponse.success(new MoveStopResponse(
                result.sourcePlan() != null ? toResponse(result.sourcePlan()) : null,
                toResponse(result.targetPlan())));
    }

    public record MoveStopResponse(
            @JsonProperty("source_plan") DispatchPlanResponse sourcePlan,
            @JsonProperty("target_plan") DispatchPlanResponse targetPlan) {
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

    @GetMapping(value = "/drivers/tracking/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time SSE GPS tracking stream for all streaming drivers in the tenant")
    public SseEmitter streamFleetTracking(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        return trackingSimulationService.subscribeFleet(requireTenantId(tenantId));
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
        List<UUID> orderIds = plan.getOrdersInStopOrder().stream()
                .map(Order::getId)
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
        List<UUID> orderIds = plan.getOrdersInStopOrder().stream()
                .map(Order::getId)
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
        List<UUID> stopIds = plan.getOrdersInStopOrder().stream()
                .map(Order::getId)
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
                stopIds.size(),
                stopIds,
                plan.getPlannedAt(),
                plan.getApprovedAt(),
                plan.getDispatchedAt(),
                plan.getAckDeadlineAt(),
                plan.getAcknowledgedAt(),
                plan.getDeclinedAt(),
                plan.getDeclineReason(),
                plan.getReassignmentCount(),
                plan.getRouteWaypoints(),
                plan.getCreatedAt(),
                plan.getVersion());
    }
}
