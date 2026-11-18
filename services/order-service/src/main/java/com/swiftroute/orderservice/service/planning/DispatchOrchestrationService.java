package com.swiftroute.orderservice.service.planning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.swiftroute.orderservice.client.RoutingEngineClient;
import com.swiftroute.orderservice.dto.request.ManualAssignmentRequest;
import com.swiftroute.orderservice.dto.request.MovePlanStopRequest;
import com.swiftroute.orderservice.dto.request.ReorderPlanStopsRequest;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest.LocationPoint;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest.StopItem;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteInsertRequest;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteInsertResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteReoptimizeRequest;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteReoptimizeResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteStopState;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.entity.enums.PlanTriggerType;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.TenantRepository;
import com.swiftroute.orderservice.service.DeliveryExecutionService;
import com.swiftroute.orderservice.service.OperationalReadinessService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core dispatch orchestration service.
 *
 * <p>
 * Responsibilities:
 * <ol>
 * <li>Collect eligible READY_FOR_DISPATCH orders for a tenant.</li>
 * <li>Build a {@link BatchOptimizationRequest} and call the routing
 * engine.</li>
 * <li>Persist the resulting {@link DispatchPlan}s in DRAFT state without
 * assigning a driver yet, and mark their orders as PLANNED.</li>
 * <li>Assign a driver and approve/dispatch the plan in separate steps
 * ({@link #approvePlan}, {@link #dispatchPlan}).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchOrchestrationService {

    private static final int DEFAULT_ACK_TIMEOUT_MINUTES = 5;
    private static final int MAX_REASSIGNMENT_ATTEMPTS = 3;
    private static final int MAX_VEHICLES_PER_BATCH = 50;

    private final TenantRepository tenantRepository;
    private final OrderRepository orderRepository;
    private final DispatchPlanRepository dispatchPlanRepository;
    private final DeliveryTaskRepository deliveryTaskRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RoutingEngineClient routingEngineClient;
    private final PlanningEligibilityEvaluator eligibilityEvaluator;
    private final OperationalReadinessService operationalReadinessService;
    private final DeliveryExecutionService deliveryExecutionService;
    private final ObjectMapper objectMapper;

    /**
     * Creates authoritative plans for explicit manual assignments in one
     * transaction.
     */
    @Transactional
    public void assignManualBatch(UUID tenantId, UUID operatorId,
            ManualAssignmentRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
        Map<UUID, List<Order>> groups = new java.util.LinkedHashMap<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (var assignment : request.assignments()) {
            if (!seen.add(assignment.orderId()) || assignment.planId() != null)
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            operationalReadinessService.requireAssignment(tenant, assignment.driverId());
            Order order = orderRepository.findById(assignment.orderId())
                    .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
            if (!tenantId.equals(order.getTenant().getId()))
                throw new AppException(ErrorCode.ACCESS_DENIED);
            if (!order.canJoinDispatchPlan())
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
            // Delivery tasks and GPS events use the authenticated user identity.
            var fleetDriver = operationalReadinessService.fleetState(tenantId).drivers().stream()
                    .filter(driver -> assignment.driverId().toString().equals(String.valueOf(driver.get("driver_id")))
                            || assignment.driverId().toString().equals(String.valueOf(driver.get("user_id"))))
                    .findFirst().orElseThrow(() -> new AppException(ErrorCode.OPERATIONS_NOT_READY));
            if (fleetDriver.get("user_id") == null)
                throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
            UUID driverUserId = UUID.fromString(fleetDriver.get("user_id").toString());
            groups.computeIfAbsent(driverUserId, ignored -> new ArrayList<>()).add(order);
        }
        Instant now = Instant.now();
        for (var group : groups.entrySet()) {
            List<Order> driverOrders = group.getValue();
            Optional<BatchOptimizationResponse> routeOpt = Optional.empty();
            try {
                routeOpt = routingEngineClient
                        .optimizeBatch(buildRoutingRequest(tenant, driverOrders, 1, driverOrders.size()));
            } catch (Exception ex) {
                log.warn("Fallback to local route metrics for manual assignment: {}", ex.getMessage());
            }

            double distanceKm = routeOpt.map(BatchOptimizationResponse::totalDistanceKm)
                    .orElseGet(() -> estimateRouteDistanceKm(tenant, driverOrders));
            int durationMin = routeOpt.map(r -> (int) Math.ceil(r.totalDurationMinutes()))
                    .orElseGet(() -> Math.max(5, (int) Math.ceil((distanceKm / 28.0) * 60.0)));
            String algoUsed = routeOpt.map(BatchOptimizationResponse::algorithmUsed)
                    .orElse("ortools");
            String waypointsJson = routeOpt.map(r -> serializeStopWaypoints(r.orderedStops()))
                    .orElseGet(() -> serializeOrdersAsWaypoints(driverOrders));

            DispatchPlan plan = dispatchPlanRepository.save(DispatchPlan.builder()
                    .tenant(tenant)
                    .driverId(group.getKey())
                    .triggerType(PlanTriggerType.MANUAL)
                    .algorithmUsed(algoUsed)
                    .totalDistanceKm(BigDecimal.valueOf(distanceKm))
                    .estimatedDurationMinutes(durationMin)
                    .routeWaypoints(waypointsJson)
                    .plannedAt(now)
                    .build());
            int index = 0;
            for (Order order : driverOrders) {
                plan.addOrder(order, index++);
                order.setStatus(OrderStatus.PLANNED);
            }
            plan.approve(operatorId);
            dispatchPlan(plan);
        }
    }

    /**
     * Executes a multi-route planning cycle for the given tenant:
     * collect → optimize → persist unassigned DRAFT plans. Drivers are assigned
     * later in a separate step.
     *
     * @param tenantId    UUID of the tenant
     * @param triggerType reason the planning cycle was triggered
     * @return the first persisted {@link DispatchPlan}, or empty if planning was
     *         skipped
     */
    @Transactional
    public Optional<DispatchPlan> executePlanningCycle(UUID tenantId, PlanTriggerType triggerType) {
        return plan(tenantId, triggerType, null).plans().stream().findFirst();
    }

    public record CycleResult(String outcome, int eligibleOrders, List<DispatchPlan> plans) {
    }

    @Transactional
    public CycleResult executeFlowCycle(UUID tenantId, PlanTriggerType triggerType, UUID pickupLocationId) {
        return plan(tenantId, triggerType, pickupLocationId);
    }

    private CycleResult plan(UUID tenantId, PlanTriggerType triggerType, UUID pickupLocationId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        Instant now = Instant.now();
        List<Order> allEligible = eligibilityEvaluator.getEligibleOrders(tenantId, now);
        if (pickupLocationId != null) {
            allEligible = allEligible.stream()
                    .filter(order -> order.getPickupLocation() != null
                            && pickupLocationId.equals(order.getPickupLocation().getId()))
                    .toList();
        }

        if (allEligible.isEmpty()) {
            log.info("Tenant {} planning cycle skipped — no eligible orders.", tenantId);
            return new CycleResult("NO_ORDERS", 0, List.of());
        }

        int maxOrdersPerDriver = (tenant.getDispatchConfig() != null
                && tenant.getDispatchConfig().getMaxOrdersPerDriver() != null)
                        ? tenant.getDispatchConfig().getMaxOrdersPerDriver()
                        : 5;

        // When triggered manually ("Chạy lập kế hoạch ngay"), process ALL warehouse
        // groups in a single run so no depot's pending orders are left behind.
        // For background triggers (BATCH_SIZE, MAX_WAIT, URGENT), process the
        // qualifying group.
        List<List<Order>> warehouseGroups = selectTargetWarehouseGroups(tenant, allEligible, triggerType);
        List<DispatchPlan> createdPlans = new ArrayList<>();

        for (List<Order> warehouseGroup : warehouseGroups) {
            if (warehouseGroup.isEmpty()) {
                continue;
            }

            int maxBatchOrders = maxOrdersPerDriver * MAX_VEHICLES_PER_BATCH;
            List<Order> eligible = warehouseGroup.size() > maxBatchOrders
                    ? warehouseGroup.subList(0, maxBatchOrders)
                    : warehouseGroup;

            int neededVehicles = (int) Math.ceil((double) eligible.size() / maxOrdersPerDriver);
            int vehicleCount = Math.clamp(neededVehicles, 1, MAX_VEHICLES_PER_BATCH);

            log.info(
                    "Tenant {} starting planning cycle for warehouse group. Trigger={} EligibleOrders={} VehicleCount={}",
                    tenantId, triggerType, eligible.size(), vehicleCount);

            BatchOptimizationRequest routingRequest = buildRoutingRequest(
                    tenant, eligible, vehicleCount, maxOrdersPerDriver);

            Optional<BatchOptimizationResponse> routingResult = routingEngineClient.optimizeBatch(routingRequest);
            if (routingResult.isEmpty()) {
                log.error("Tenant {} planning cycle skipped warehouse group — routing engine returned no result.",
                        tenantId);
                continue;
            }
            BatchOptimizationResponse routingResponse = routingResult.get();

            List<BatchOptimizationResponse.VehicleRoute> vehicleRoutes = extractVehicleRoutes(routingResponse);
            Map<String, Order> eligibleById = new LinkedHashMap<>();
            eligible.forEach(order -> eligibleById.put(order.getId().toString(), order));
            if (!hasValidUniqueStops(vehicleRoutes, eligibleById)) {
                log.warn(
                        "Tenant {} planning skipped a warehouse group because the routing result had duplicate or unknown stops.",
                        tenantId);
                continue;
            }

            for (BatchOptimizationResponse.VehicleRoute vRoute : vehicleRoutes) {
                DispatchPlan savedPlan = persistDraftPlan(tenant, triggerType, routingResponse, vRoute, eligibleById, now);
                createdPlans.add(savedPlan);
            }
        }

        return new CycleResult(createdPlans.isEmpty() ? "NO_ROUTES" : "PLANNED", allEligible.size(), createdPlans);
    }

    private List<BatchOptimizationResponse.VehicleRoute> extractVehicleRoutes(
            BatchOptimizationResponse routingResponse) {
        if (routingResponse.vehicleRoutes() != null && !routingResponse.vehicleRoutes().isEmpty()) {
            return routingResponse.vehicleRoutes().stream()
                    .filter(vr -> vr.orderedStops() != null && !vr.orderedStops().isEmpty())
                    .toList();
        }
        if (routingResponse.orderedStops() == null || routingResponse.orderedStops().isEmpty()) {
            return List.of();
        }
        return List.of(new BatchOptimizationResponse.VehicleRoute(
                0,
                routingResponse.totalDistanceKm(),
                routingResponse.totalDurationMinutes(),
                0.0,
                routingResponse.orderedStops()));
    }

    private boolean hasValidUniqueStops(
            List<BatchOptimizationResponse.VehicleRoute> vehicleRoutes,
            Map<String, Order> eligibleById) {
        Set<String> seenStops = new HashSet<>();
        return vehicleRoutes.stream()
                .flatMap(route -> route.orderedStops().stream())
                .allMatch(stop -> eligibleById.containsKey(stop.stopId()) && seenStops.add(stop.stopId()));
    }

    private DispatchPlan persistDraftPlan(
            Tenant tenant,
            PlanTriggerType triggerType,
            BatchOptimizationResponse routingResponse,
            BatchOptimizationResponse.VehicleRoute vRoute,
            Map<String, Order> eligibleById,
            Instant now) {
        DispatchPlan plan = dispatchPlanRepository.save(DispatchPlan.builder()
                .tenant(tenant)
                .planStatus(PlanStatus.DRAFT)
                .driverId(null)
                .algorithmUsed(routingResponse.algorithmUsed())
                .totalDistanceKm(BigDecimal.valueOf(vRoute.totalDistanceKm()))
                .estimatedDurationMinutes((int) Math.ceil(vRoute.totalDurationMinutes()))
                .triggerType(triggerType)
                .plannedAt(now)
                .routeWaypoints(serializeStopWaypoints(vRoute.orderedStops()))
                .build());

        List<Order> routeOrders = new ArrayList<>();
        int stopIndex = 1;
        for (BatchOptimizationResponse.OrderedStop stop : vRoute.orderedStops()) {
            Order order = eligibleById.get(stop.stopId());
            plan.addOrder(order, stopIndex++);
            order.setStatus(OrderStatus.PLANNED);
            order.setUpdatedAt(now);
            routeOrders.add(order);
        }
        orderRepository.saveAll(routeOrders);

        DispatchPlan persistedPlan = dispatchPlanRepository.save(plan);
        log.info("Tenant {} dispatch plan {} saved in DRAFT (unassigned). Orders={}",
                tenant.getId(), persistedPlan.getId(), routeOrders.size());
        return persistedPlan;
    }

    /**
     * Transitions an APPROVED plan to DISPATCHED, creates/updates delivery tasks
     * for the assigned driver, and publishes a {@code plan.dispatched} outbox
     * event.
     *
     * @param plan the plan to dispatch (must be in APPROVED state)
     * @return the saved plan in DISPATCHED state
     */
    @Transactional
    public DispatchPlan dispatchPlan(DispatchPlan plan) {
        UUID driverId = plan.getDriverId();
        if (driverId == null) {
            throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
        }
        Instant ackDeadline = Instant.now().plusSeconds(DEFAULT_ACK_TIMEOUT_MINUTES * 60L);
        try {
            plan.dispatch(ackDeadline);
        } catch (IllegalStateException ex) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }

        for (Order order : plan.getOrders()) {
            if (!driverId.equals(order.getAssignedDriverId()) && deliveryExecutionService.holdsOrder(order)) {
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
            }
            order.setAssignedDriverId(driverId);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            DeliveryTask task = deliveryExecutionService.assign(order, driverId);
            task.setDispatchPlanId(plan.getId());
            deliveryTaskRepository.save(task);
        }

        DispatchPlan saved = dispatchPlanRepository.save(plan);
        publishOutboxEvent(saved, "plan.dispatched");

        log.info("Plan {} dispatched. Driver={} AckDeadline={}", saved.getId(), saved.getDriverId(), ackDeadline);
        return saved;
    }

    @Transactional
    public DispatchPlan reorderDraftStops(UUID tenantId, UUID planId, ReorderPlanStopsRequest request) {
        DispatchPlan plan = loadPlan(planId);
        if (!plan.getTenant().getId().equals(tenantId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (!Objects.equals(plan.getVersion(), request.expectedVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }
        if (plan.getPlanStatus() != PlanStatus.DRAFT || plan.getDriverId() != null) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }

        List<UUID> requestedIds = request.orderedStopIds();
        Map<UUID, Order> stops = new LinkedHashMap<>();
        plan.getOrders().forEach(order -> stops.put(order.getId(), order));
        if (requestedIds.size() != stops.size() || !new HashSet<>(requestedIds).equals(stops.keySet())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        List<UUID> currentIds = plan.getOrdersInStopOrder().stream()
                .map(Order::getId)
                .toList();
        if (currentIds.equals(requestedIds)) {
            return plan;
        }

        List<Order> orderedOrders = requestedIds.stream().map(stops::get).toList();
        BatchOptimizationResponse measured = routingEngineClient.measureOrderedRoute(
                buildRoutingRequest(plan.getTenant(), orderedOrders, 1, orderedOrders.size()))
                .orElseThrow(() -> new AppException(ErrorCode.ROUTE_MEASUREMENT_UNAVAILABLE));
        if (measured.orderedStops() == null || !measured.orderedStops().stream()
                .map(BatchOptimizationResponse.OrderedStop::stopId).toList()
                .equals(requestedIds.stream().map(UUID::toString).toList())) {
            throw new AppException(ErrorCode.ROUTE_MEASUREMENT_UNAVAILABLE);
        }

        for (int index = 0; index < requestedIds.size(); index++) {
            stops.get(requestedIds.get(index)).setPlanStopIndex(index + 1);
        }
        plan.setRouteWaypoints(serializeStopWaypoints(measured.orderedStops()));
        plan.setTotalDistanceKm(BigDecimal.valueOf(measured.totalDistanceKm()));
        plan.setEstimatedDurationMinutes((int) Math.ceil(measured.totalDurationMinutes()));
        plan.setUpdatedAt(Instant.now());
        return dispatchPlanRepository.saveAndFlush(plan);
    }

    public record MoveStopResult(DispatchPlan sourcePlan, DispatchPlan targetPlan) {}

    /**
     * Moves one stop between two unassigned DRAFT plans of the same tenant and
     * re-measures both routes so distance, duration and per-stop ETAs stay accurate.
     * A source plan left without stops is cancelled.
     */
    @Transactional
    public MoveStopResult moveDraftStop(UUID tenantId, MovePlanStopRequest request) {
        if (request.sourcePlanId().equals(request.targetPlanId())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        DispatchPlan source = loadPlan(request.sourcePlanId());
        DispatchPlan target = loadPlan(request.targetPlanId());
        for (DispatchPlan plan : List.of(source, target)) {
            if (!plan.getTenant().getId().equals(tenantId)) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            if (plan.getPlanStatus() != PlanStatus.DRAFT || plan.getDriverId() != null) {
                throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
            }
        }
        if (!Objects.equals(source.getVersion(), request.expectedSourceVersion())
                || !Objects.equals(target.getVersion(), request.expectedTargetVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }

        Order moved = source.getOrders().stream()
                .filter(order -> order.getId().equals(request.orderId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR));

        List<Order> sourceRemaining = new ArrayList<>(source.getOrdersInStopOrder());
        sourceRemaining.remove(moved);
        List<Order> targetOrdered = new ArrayList<>(target.getOrdersInStopOrder());
        int insertAt = Math.min(request.targetIndex(), targetOrdered.size());
        targetOrdered.add(insertAt, moved);

        // Measure both routes before touching any state so a routing failure changes nothing.
        BatchOptimizationResponse targetMeasured = measureDraftRoute(target, targetOrdered);
        BatchOptimizationResponse sourceMeasured = sourceRemaining.isEmpty()
                ? null
                : measureDraftRoute(source, sourceRemaining);

        source.removeOrder(moved);
        target.addOrder(moved, insertAt + 1);
        applyMeasuredRoute(target, targetOrdered, targetMeasured);
        DispatchPlan savedTarget = dispatchPlanRepository.saveAndFlush(target);
        if (sourceMeasured == null) {
            discardPlan(source, false);
            return new MoveStopResult(null, savedTarget);
        }
        applyMeasuredRoute(source, sourceRemaining, sourceMeasured);
        return new MoveStopResult(dispatchPlanRepository.saveAndFlush(source), savedTarget);
    }

    private BatchOptimizationResponse measureDraftRoute(DispatchPlan plan, List<Order> ordered) {
        BatchOptimizationResponse measured = routingEngineClient.measureOrderedRoute(
                buildRoutingRequest(plan.getTenant(), ordered, 1, ordered.size()))
                .orElseThrow(() -> new AppException(ErrorCode.ROUTE_MEASUREMENT_UNAVAILABLE));
        List<String> expected = ordered.stream().map(order -> order.getId().toString()).toList();
        if (measured.orderedStops() == null || !measured.orderedStops().stream()
                .map(BatchOptimizationResponse.OrderedStop::stopId).toList().equals(expected)) {
            throw new AppException(ErrorCode.ROUTE_MEASUREMENT_UNAVAILABLE);
        }
        return measured;
    }

    private void applyMeasuredRoute(DispatchPlan plan, List<Order> ordered, BatchOptimizationResponse measured) {
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setPlanStopIndex(index + 1);
        }
        plan.setRouteWaypoints(serializeStopWaypoints(measured.orderedStops()));
        plan.setTotalDistanceKm(BigDecimal.valueOf(measured.totalDistanceKm()));
        plan.setEstimatedDurationMinutes((int) Math.ceil(measured.totalDurationMinutes()));
        plan.setUpdatedAt(Instant.now());
    }

    /**
     * Manually approves a DRAFT plan (operator action from Web Admin).
     *
     * @param planId         UUID of the plan
     * @param approvedByUser UUID of the approving operator
     * @param driverId       optional driver UUID override
     * @return approved plan
     */
    @Transactional
    public DispatchPlan approvePlan(UUID planId, UUID approvedByUser, UUID driverId) {
        DispatchPlan plan = loadPlan(planId);
        if (driverId != null) {
            plan.setDriverId(driverId);
        }
        if (plan.getDriverId() == null) {
            throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
        }
        try {
            plan.approve(approvedByUser);
        } catch (IllegalStateException ex) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }
        plan = dispatchPlanRepository.save(plan);
        log.info("Plan {} approved by user {}.", planId, approvedByUser);
        return plan;
    }

    /**
     * Cancels and removes a non-completed plan, returning its stops to the dispatch queue.
     *
     * @param planId UUID of the plan
     * @param reason cancellation reason for logs/audit
     * @return the removed plan snapshot
     */
    @Transactional
    public DispatchPlan cancelPlan(UUID planId, String reason) {
        DispatchPlan plan = loadPlan(planId);
        requireNoHeldOrders(plan);
        discardPlan(plan, true);
        log.info("Plan {} cancelled and removed. Reason={}", planId, reason);
        return plan;
    }

    /**
     * Records driver acknowledgement for a DISPATCHED plan and transitions
     * associated ASSIGNED delivery tasks to ACCEPTED.
     */
    @Transactional
    public DispatchPlan acknowledgeByDriver(UUID planId) {
        DispatchPlan plan = loadPlan(planId);
        try {
            plan.acknowledge();
        } catch (IllegalStateException ex) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }

        for (Order order : plan.getOrders()) {
            deliveryTaskRepository.findByOrder_IdAndTaskType(order.getId(), "DELIVERY")
                    .ifPresent(task -> {
                        if (task.getStatus() == DeliveryTaskStatus.ASSIGNED) {
                            task.setStatus(DeliveryTaskStatus.ACCEPTED);
                            task.setUpdatedAt(Instant.now());
                            deliveryTaskRepository.save(task);
                        }
                    });
        }

        DispatchPlan saved = dispatchPlanRepository.save(plan);
        publishOutboxEvent(saved, "plan.acknowledged");
        return saved;
    }

    /**
     * Records driver decline for a DISPATCHED plan.
     * Returns affected orders to the dispatch queue and cancels the plan.
     */
    @Transactional
    public DispatchPlan declineByDriver(UUID planId, String reason) {
        DispatchPlan plan = loadPlan(planId);
        requireNoHeldOrders(plan);
        try {
            plan.decline(reason);
        } catch (IllegalStateException ex) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }

        plan.decline(reason);
        discardPlan(plan, true);
        return plan;
    }

    /**
     * Handles acknowledgement timeout for a DISPATCHED plan (P2.1 & P2.2).
     * Retries reassignment up to {@link #MAX_REASSIGNMENT_ATTEMPTS} times before
     * cancelling and escalating to manual operator review.
     */
    @Transactional
    public DispatchPlan handleAcknowledgementTimeout(UUID planId) {
        return handleAcknowledgementTimeout(planId, java.util.Set.of());
    }

    @Transactional
    public DispatchPlan handleAcknowledgementTimeout(UUID planId, java.util.Set<UUID> ineligibleDriverIds) {
        DispatchPlan plan = loadPlan(planId);
        if (plan.getPlanStatus() != PlanStatus.DISPATCHED || plan.getAcknowledgedAt() != null) {
            return plan;
        }

        // Starting execution is an implicit acceptance of the dispatched route.
        // Never reassign or return its orders to planning after the driver has
        // accepted a task, picked up custody, or begun/finished delivery.
        if (hasClaimedDeliveryExecution(plan) || plan.getOrders().stream()
                .anyMatch(deliveryExecutionService::holdsOrder)) {
            plan.acknowledge();
            DispatchPlan saved = dispatchPlanRepository.save(plan);
            publishOutboxEvent(saved, "plan.acknowledged_implicitly");
            log.warn("Plan {} passed its acknowledgement deadline after delivery execution started; "
                    + "recorded implicit acknowledgement and preserved the active route.", planId);
            return saved;
        }

        short nextCount = (short) ((plan.getReassignmentCount() != null ? plan.getReassignmentCount() : 0) + 1);
        plan.setReassignmentCount(nextCount);
        // Do not spam timeout alert to notification inbox

        UUID tenantId = plan.getTenant().getId();
        java.util.Set<UUID> reservedDriverIds = new java.util.HashSet<>(ineligibleDriverIds);
        dispatchPlanRepository.findAllByTenantIdAndPlanStatusIn(
                tenantId, List.of(PlanStatus.APPROVED, PlanStatus.DISPATCHED))
                .stream()
                .map(DispatchPlan::getDriverId)
                .filter(java.util.Objects::nonNull)
                .forEach(reservedDriverIds::add);
        UUID alternativeDriver = selectAlternativeDriver(
                tenantId, plan.getDriverId(), reservedDriverIds);

        if (nextCount <= MAX_REASSIGNMENT_ATTEMPTS && alternativeDriver != null) {
            plan.setDriverId(alternativeDriver);
            plan.setAckDeadlineAt(Instant.now().plusSeconds(DEFAULT_ACK_TIMEOUT_MINUTES * 60L));
            plan.setUpdatedAt(Instant.now());

            for (Order order : plan.getOrders()) {
                order.setAssignedDriverId(alternativeDriver);
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);

                DeliveryTask task = deliveryExecutionService.assign(order, alternativeDriver);
                task.setDispatchPlanId(plan.getId());
                deliveryTaskRepository.save(task);
            }

            DispatchPlan saved = dispatchPlanRepository.save(plan);
            publishOutboxEvent(saved, "plan.dispatched");
            log.info("Plan {} reassigned on ack timeout (attempt {}/{}) to driver {}.",
                    planId, nextCount, MAX_REASSIGNMENT_ATTEMPTS, alternativeDriver);
            return saved;
        }

        // Max retries reached or no alternative driver available -> discard plan & escalate
        discardPlan(plan, false);
        log.warn("Plan {} discarded after {} reassignment attempts.", planId, nextCount);
        return plan;
    }

    private void discardPlan(DispatchPlan plan, boolean publishOutbox) {
        if (plan.getPlanStatus() == PlanStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }
        if (publishOutbox) {
            publishOutboxEvent(plan, "plan.cancelled");
        }
        releasePlanStops(plan);
        if (plan.getId() != null) {
            dispatchPlanRepository.clearIncidentReferences(plan.getId());
            dispatchPlanRepository.clearTaskReferences(plan.getId());
        }
        dispatchPlanRepository.delete(plan);
    }

    private void releasePlanStops(DispatchPlan plan) {
        List<Order> released = plan.getOrders().stream()
                .filter(order -> order.getStatus() == OrderStatus.PLANNED || order.getStatus() == OrderStatus.IN_TRANSIT)
                .toList();
        for (Order order : released) {
            plan.removeOrder(order);
            order.setStatus(OrderStatus.READY_FOR_DISPATCH);
            order.setAssignedDriverId(null);
            orderRepository.save(order);
        }
        retainCurrentWaypoints(plan);
    }

    private void retainCurrentWaypoints(DispatchPlan plan) {
        if (plan.getOrders().isEmpty() || plan.getRouteWaypoints() == null) {
            plan.setRouteWaypoints("[]");
            return;
        }

        Set<String> currentOrderIds = new HashSet<>();
        plan.getOrders().forEach(order -> currentOrderIds.add(order.getId().toString()));
        ArrayNode retained = objectMapper.createArrayNode();
        try {
            JsonNode waypoints = objectMapper.readTree(plan.getRouteWaypoints());
            if (waypoints != null && waypoints.isArray()) {
                for (JsonNode waypoint : waypoints) {
                    String orderId = waypoint.path("stop_id").asText(waypoint.path("stopId").asText());
                    if (currentOrderIds.contains(orderId)) {
                        retained.add(waypoint);
                    }
                }
            }
        } catch (JsonProcessingException ex) {
            log.warn("Discarded unreadable route waypoints for plan {}", plan.getId());
        }
        plan.setRouteWaypoints(retained.toString());
    }

    private void requireNoHeldOrders(DispatchPlan plan) {
        if (plan.getOrders().stream().anyMatch(deliveryExecutionService::holdsOrder)) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }
    }

    private boolean hasClaimedDeliveryExecution(DispatchPlan plan) {
        return plan.getOrders().stream()
                .map(Order::getId)
                .map(orderId -> deliveryTaskRepository.findByOrder_IdAndTaskType(orderId, "DELIVERY"))
                .flatMap(Optional::stream)
                .map(DeliveryTask::getStatus)
                .anyMatch(status -> status != DeliveryTaskStatus.ASSIGNED
                        && status != DeliveryTaskStatus.CANCELLED);
    }

    /**
     * Moves one physical package responsibility between drivers and keeps every
     * execution projection in sync. Delivery transfers move the stop between
     * active plans; return transfers only remove the stale delivery stop.
     */
    @Transactional
    public void synchronizePackageTransfer(
            Order order,
            UUID fromDriverId,
            UUID toDriverId,
            String taskType) {
        if (order == null
                || order.getStatus() == OrderStatus.DELIVERED
                || fromDriverId == null
                || toDriverId == null
                || fromDriverId.equals(toDriverId)
                || !Set.of("DELIVERY", "RETURN").contains(taskType)
                || !fromDriverId.equals(order.getAssignedDriverId())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        DeliveryTask task = deliveryTaskRepository.findByOrder_IdAndTaskType(order.getId(), taskType)
                .orElseGet(() -> DeliveryTask.builder()
                        .order(order)
                        .tenantId(order.getTenant().getId())
                        .driverId(fromDriverId)
                        .taskType(taskType)
                        .status(DeliveryTaskStatus.OUT_FOR_DELIVERY)
                        .build());
        if (task.getStatus() == DeliveryTaskStatus.COMPLETED
                || task.getStatus() == DeliveryTaskStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        UUID sourcePlanId = order.getPlanId();
        DispatchPlan sourcePlan = sourcePlanId == null ? null : loadPlan(sourcePlanId);
        if (sourcePlan != null) {
            detachTransferredOrder(sourcePlan, order, fromDriverId);
        }

        DispatchPlan targetPlan = null;
        if ("DELIVERY".equals(taskType)) {
            targetPlan = findOrCreateTransferTargetPlan(order, toDriverId);
            attachTransferredOrder(targetPlan, order);
        }

        Instant now = Instant.now();
        order.setAssignedDriverId(toDriverId);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        task.setDriverId(toDriverId);
        task.setDispatchPlanId(targetPlan != null ? targetPlan.getId() : null);
        task.setUpdatedAt(now);
        deliveryTaskRepository.save(task);

        log.info(
                "Package transfer synchronized: order={}, taskType={}, fromDriver={}, toDriver={}, sourcePlan={}, targetPlan={}",
                order.getId(), taskType, fromDriverId, toDriverId, sourcePlanId,
                targetPlan != null ? targetPlan.getId() : null);
    }

    private void detachTransferredOrder(DispatchPlan plan, Order order, UUID fromDriverId) {
        if (!fromDriverId.equals(plan.getDriverId()) || !plan.removeOrder(order)) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }

        if (plan.getPlanStatus() == PlanStatus.COMPLETED) {
            retainCurrentWaypoints(plan);
        } else if (plan.getOrders().isEmpty()) {
            if (plan.getPlanStatus() == PlanStatus.DISPATCHED) {
                plan.complete();
                plan.setRouteWaypoints("[]");
                plan.setTotalDistanceKm(BigDecimal.ZERO);
                plan.setEstimatedDurationMinutes(0);
            } else {
                discardPlan(plan, false);
                return;
            }
        } else {
            refreshRemainingRoute(plan, "PACKAGE_HANDOVER");
        }
        plan.setUpdatedAt(Instant.now());
        dispatchPlanRepository.save(plan);
        publishOutboxEvent(plan, "route.updated");
    }

    private DispatchPlan findOrCreateTransferTargetPlan(Order order, UUID toDriverId) {
        List<DispatchPlan> candidates = dispatchPlanRepository.findAllByDriverIdAndPlanStatusIn(
                toDriverId, List.of(PlanStatus.DISPATCHED));
        Optional<DispatchPlan> existing = candidates.stream()
                .filter(plan -> plan.getTenant().getId().equals(order.getTenant().getId()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = Instant.now();
        DispatchPlan plan = DispatchPlan.builder()
                .tenant(order.getTenant())
                .driverId(toDriverId)
                .triggerType(PlanTriggerType.MANUAL)
                .algorithmUsed("package-handover")
                .routeWaypoints("[]")
                .totalDistanceKm(BigDecimal.ZERO)
                .estimatedDurationMinutes(0)
                .plannedAt(now)
                .requiresApproval(false)
                .build();
        plan.approve(null);
        plan.dispatch(now.plusSeconds(DEFAULT_ACK_TIMEOUT_MINUTES * 60L));
        plan.acknowledge();
        return dispatchPlanRepository.saveAndFlush(plan);
    }

    private void attachTransferredOrder(DispatchPlan plan, Order order) {
        if (order.getPlanId() != null) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }

        boolean newlyCreatedTransferPlan = plan.getOrders().isEmpty()
                && "package-handover".equals(plan.getAlgorithmUsed());
        if (plan.getOrders().isEmpty()) {
            plan.addOrder(order, 0);
            refreshLocalRouteSnapshot(plan);
        } else {
            LocationPoint driverLoc = resolvePlanOrigin(plan, null, null);
            List<RouteStopState> currentStops = buildCurrentStopStates(plan);
            StopItem newStop = new StopItem(
                    order.getId().toString(),
                    order.getRecipientName(),
                    new LocationPoint(order.getDeliveryLat(), order.getDeliveryLng(), order.getDeliveryAddress()),
                    order.getWeightKg() != null ? order.getWeightKg() : 0.0,
                    order.getTimeWindowStart() != null ? order.getTimeWindowStart().toInstant().toString() : null,
                    order.getTimeWindowEnd() != null ? order.getTimeWindowEnd().toInstant().toString() : null);
            double[] weights = optimizationWeights(plan.getTenant());
            Optional<RouteInsertResponse> result = routingEngineClient.optimizeInsert(
                    new RouteInsertRequest(driverLoc, currentStops, newStop, 45.0, weights[0], weights[1]));
            if (result.isEmpty() || !result.get().feasible()) {
                throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
            }
            RouteInsertResponse inserted = result.get();
            applyUpdatedStopSequence(plan, inserted.orderedStops(), order);
            plan.setTotalDistanceKm(BigDecimal.valueOf(inserted.totalDistanceKm()));
            plan.setEstimatedDurationMinutes((int) Math.ceil(inserted.totalDurationMinutes()));
            plan.setRouteWaypoints(serializeStopWaypoints(inserted.orderedStops()));
        }
        plan.setUpdatedAt(Instant.now());
        dispatchPlanRepository.save(plan);
        publishOutboxEvent(plan, newlyCreatedTransferPlan ? "plan.dispatched" : "route.updated");
    }

    private void refreshRemainingRoute(DispatchPlan plan, String reason) {
        if (plan.getPlanStatus() != PlanStatus.DISPATCHED) {
            refreshLocalRouteSnapshot(plan);
            return;
        }
        double[] weights = optimizationWeights(plan.getTenant());
        Optional<RouteReoptimizeResponse> result = routingEngineClient.optimizeReoptimize(
                new RouteReoptimizeRequest(
                        resolvePlanOrigin(plan, null, null),
                        buildCurrentStopStates(plan),
                        reason,
                        0.0,
                        weights[0],
                        weights[1]));
        if (result.isEmpty()) {
            refreshLocalRouteSnapshot(plan);
            return;
        }
        RouteReoptimizeResponse refreshed = result.get();
        applyUpdatedStopSequence(plan, refreshed.orderedStops(), null);
        plan.setTotalDistanceKm(BigDecimal.valueOf(refreshed.totalDistanceKm()));
        plan.setEstimatedDurationMinutes((int) Math.ceil(refreshed.totalDurationMinutes()));
        plan.setRouteWaypoints(serializeStopWaypoints(refreshed.orderedStops()));
    }

    private void refreshLocalRouteSnapshot(DispatchPlan plan) {
        List<Order> remaining = plan.getOrdersInStopOrder();
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setPlanStopIndex(index);
        }
        double distanceKm = estimateRouteDistanceKm(plan.getTenant(), remaining);
        plan.setRouteWaypoints(serializeOrdersAsWaypoints(remaining));
        plan.setTotalDistanceKm(BigDecimal.valueOf(distanceKm));
        plan.setEstimatedDurationMinutes(Math.max(5, (int) Math.ceil((distanceKm / 28.0) * 60.0)));
    }

    private double[] optimizationWeights(Tenant tenant) {
        double timeWeight = tenant.getOptimizationWeights() != null
                && tenant.getOptimizationWeights().getTimeWeight() != null
                        ? tenant.getOptimizationWeights().getTimeWeight()
                        : 0.5;
        double costWeight = tenant.getOptimizationWeights() != null
                && tenant.getOptimizationWeights().getCostWeight() != null
                        ? tenant.getOptimizationWeights().getCostWeight()
                        : 0.5;
        return new double[] { timeWeight, costWeight };
    }

    /**
     * Dynamically inserts a new order into an active DISPATCHED plan using OR-Tools
     * POST /optimize/insert while preserving completed stops (P3.4).
     */
    @Transactional
    public DispatchPlan insertOrderIntoActivePlan(
            UUID planId,
            UUID newOrderId,
            Double driverLat,
            Double driverLng) {
        DispatchPlan plan = loadPlan(planId);
        if (plan.getPlanStatus() != PlanStatus.DISPATCHED) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }

        Order newOrder = orderRepository.findById(newOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!newOrder.getTenant().getId().equals(plan.getTenant().getId())) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        if (!newOrder.canJoinDispatchPlan()) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        Tenant tenant = plan.getTenant();
        LocationPoint driverLoc = resolvePlanOrigin(plan, driverLat, driverLng);

        List<RouteStopState> currentStops = buildCurrentStopStates(plan);
        StopItem newStop = new StopItem(
                newOrder.getId().toString(),
                newOrder.getRecipientName(),
                new LocationPoint(newOrder.getDeliveryLat(), newOrder.getDeliveryLng(), newOrder.getDeliveryAddress()),
                newOrder.getWeightKg() != null ? newOrder.getWeightKg() : 0.0,
                newOrder.getTimeWindowStart() != null ? newOrder.getTimeWindowStart().toInstant().toString() : null,
                newOrder.getTimeWindowEnd() != null ? newOrder.getTimeWindowEnd().toInstant().toString() : null);

        double timeW = tenant.getOptimizationWeights() != null
                && tenant.getOptimizationWeights().getTimeWeight() != null
                        ? tenant.getOptimizationWeights().getTimeWeight()
                        : 0.5;
        double costW = tenant.getOptimizationWeights() != null
                && tenant.getOptimizationWeights().getCostWeight() != null
                        ? tenant.getOptimizationWeights().getCostWeight()
                        : 0.5;

        Optional<RouteInsertResponse> insertResOpt = routingEngineClient.optimizeInsert(
                new RouteInsertRequest(driverLoc, currentStops, newStop, 45.0, timeW, costW));

        if (insertResOpt.isEmpty() || !insertResOpt.get().feasible()) {
            log.warn("Dynamic insertion of order {} into plan {} is infeasible or failed.", newOrderId, planId);
            return plan;
        }

        RouteInsertResponse insertRes = insertResOpt.get();
        applyUpdatedStopSequence(plan, insertRes.orderedStops(), newOrder);
        plan.setTotalDistanceKm(BigDecimal.valueOf(insertRes.totalDistanceKm()));
        plan.setEstimatedDurationMinutes((int) Math.ceil(insertRes.totalDurationMinutes()));
        try {
            plan.setRouteWaypoints(objectMapper.writeValueAsString(insertRes.orderedStops()));
        } catch (JsonProcessingException ignored) {
        }
        plan.setUpdatedAt(Instant.now());

        newOrder.setStatus(OrderStatus.PLANNED);
        if (plan.getDriverId() != null) {
            newOrder.setAssignedDriverId(plan.getDriverId());
            DeliveryTask task = deliveryExecutionService.assign(newOrder, plan.getDriverId());
            task.setDispatchPlanId(plan.getId());
            deliveryTaskRepository.save(task);
        }
        orderRepository.save(newOrder);

        DispatchPlan saved = dispatchPlanRepository.save(plan);
        publishOutboxEvent(saved, "route.updated");
        log.info("Order {} dynamically inserted into active plan {} at stopIndex={}",
                newOrderId, planId, insertRes.insertedStopIndex());
        return saved;
    }

    /**
     * Safely reoptimizes the remaining uncompleted stops of an active DISPATCHED
     * plan using OR-Tools POST /optimize/reoptimize (P3.3 & P3.4).
     */
    @Transactional
    public DispatchPlan reoptimizeActivePlan(
            UUID planId,
            String reason,
            Double driverLat,
            Double driverLng) {
        DispatchPlan plan = loadPlan(planId);
        if (plan.getPlanStatus() != PlanStatus.DISPATCHED) {
            return plan;
        }

        Tenant tenant = plan.getTenant();
        LocationPoint driverLoc = resolvePlanOrigin(plan, driverLat, driverLng);

        List<RouteStopState> currentStops = buildCurrentStopStates(plan);
        double timeW = tenant.getOptimizationWeights() != null
                && tenant.getOptimizationWeights().getTimeWeight() != null
                        ? tenant.getOptimizationWeights().getTimeWeight()
                        : 0.5;
        double costW = tenant.getOptimizationWeights() != null
                && tenant.getOptimizationWeights().getCostWeight() != null
                        ? tenant.getOptimizationWeights().getCostWeight()
                        : 0.5;

        Optional<RouteReoptimizeResponse> reoptOpt = routingEngineClient.optimizeReoptimize(
                new RouteReoptimizeRequest(driverLoc, currentStops, reason, 0.2, timeW, costW));

        if (reoptOpt.isEmpty() || !reoptOpt.get().stopsChanged()) {
            log.info("Reoptimization for plan {} produced no significant improvement; keeping existing sequence.",
                    planId);
            return plan;
        }

        RouteReoptimizeResponse reopt = reoptOpt.get();
        applyUpdatedStopSequence(plan, reopt.orderedStops(), null);
        plan.setTotalDistanceKm(BigDecimal.valueOf(reopt.totalDistanceKm()));
        plan.setEstimatedDurationMinutes((int) Math.ceil(reopt.totalDurationMinutes()));
        try {
            plan.setRouteWaypoints(objectMapper.writeValueAsString(reopt.orderedStops()));
        } catch (JsonProcessingException ignored) {
        }
        plan.setUpdatedAt(Instant.now());

        DispatchPlan saved = dispatchPlanRepository.save(plan);
        publishOutboxEvent(saved, "route.updated");
        log.info("Active plan {} reoptimized (reason={}). Saved {} km.",
                planId, reason, reopt.distanceSavedKm());
        return saved;
    }

    private List<RouteStopState> buildCurrentStopStates(DispatchPlan plan) {
        return plan.getOrdersInStopOrder().stream()
                .map(o -> {
                    boolean completed = o.getStatus() == OrderStatus.DELIVERED
                            || deliveryTaskRepository.findByOrder_IdAndTaskType(o.getId(), "DELIVERY")
                                    .map(t -> t.getStatus() == DeliveryTaskStatus.COMPLETED)
                                    .orElse(false);
                    return new RouteStopState(
                            o.getId().toString(),
                            o.getRecipientName(),
                            new LocationPoint(o.getDeliveryLat(), o.getDeliveryLng(), o.getDeliveryAddress()),
                            o.getWeightKg() != null ? o.getWeightKg() : 0.0,
                            o.getTimeWindowStart() != null ? o.getTimeWindowStart().toInstant().toString() : null,
                            o.getTimeWindowEnd() != null ? o.getTimeWindowEnd().toInstant().toString() : null,
                            completed);
                })
                .toList();
    }

    private void applyUpdatedStopSequence(
            DispatchPlan plan,
            List<BatchOptimizationResponse.OrderedStop> orderedStops,
            Order newlyInsertedOrder) {
        Map<String, Order> ordersById = new LinkedHashMap<>();
        plan.getOrders().forEach(order -> ordersById.put(order.getId().toString(), order));
        if (newlyInsertedOrder != null) {
            ordersById.put(newlyInsertedOrder.getId().toString(), newlyInsertedOrder);
        }

        Set<String> returnedIds = new HashSet<>();
        if (orderedStops == null || orderedStops.size() != ordersById.size()
                || orderedStops.stream().anyMatch(stop ->
                        !ordersById.containsKey(stop.stopId()) || !returnedIds.add(stop.stopId()))) {
            throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
        }

        int stopIndex = 1;
        for (BatchOptimizationResponse.OrderedStop stop : orderedStops) {
            Order order = ordersById.get(stop.stopId());
            if (order == newlyInsertedOrder) {
                plan.addOrder(order, stopIndex);
            } else {
                order.setPlanStopIndex(stopIndex);
            }
            stopIndex++;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private UUID selectCandidateDriver(UUID tenantId) {
        return selectAlternativeDriver(tenantId, null);
    }

    private UUID selectAlternativeDriver(UUID tenantId, UUID excludeDriverId) {
        return selectAlternativeDriver(tenantId, excludeDriverId, java.util.Set.of());
    }

    private UUID selectAlternativeDriver(
            UUID tenantId,
            UUID excludeDriverId,
            java.util.Set<UUID> ineligibleDriverIds) {
        try {
            OperationalReadinessService.FleetState fleet = operationalReadinessService.fleetState(tenantId);
            if (fleet.available() && !fleet.drivers().isEmpty()) {
                for (Map<String, Object> driver : fleet.drivers()) {
                    Object rawId = driver.get("user_id") != null ? driver.get("user_id") : driver.get("driver_id");
                    if (rawId != null) {
                        UUID candidate = UUID.fromString(String.valueOf(rawId));
                        if (!candidate.equals(excludeDriverId) && !ineligibleDriverIds.contains(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Could not fetch candidate driver for tenant {}: {}", tenantId, ex.getMessage());
        }
        return null;
    }

    private DispatchPlan loadPlan(UUID planId) {
        return dispatchPlanRepository.findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
    }

    /**
     * Selects the target warehouse order groups for a planning cycle:
     * <ul>
     * <li>When {@code triggerType == MANUAL} ("Chạy lập kế hoạch ngay"), returns
     * <strong>all</strong> warehouse groups so every depot's pending orders
     * are planned in a single run.</li>
     * <li>When triggered automatically in the background, returns the single
     * qualifying warehouse group via {@link #selectCompatibleWarehouseOrders}.</li>
     * </ul>
     */
    private List<List<Order>> selectTargetWarehouseGroups(
            Tenant tenant, List<Order> allEligible, PlanTriggerType triggerType) {
        if (allEligible.isEmpty()) {
            return List.of();
        }
        if (triggerType == PlanTriggerType.MANUAL) {
            Map<String, List<Order>> groups = new LinkedHashMap<>();
            for (Order order : allEligible) {
                groups.computeIfAbsent(pickupOriginKey(order), ignored -> new ArrayList<>()).add(order);
            }
            return new ArrayList<>(groups.values());
        }
        return List.of(selectCompatibleWarehouseOrders(tenant, allEligible));
    }

    private List<Order> selectCompatibleWarehouseOrders(Tenant tenant, List<Order> allEligible) {
        if (allEligible.isEmpty()) {
            return allEligible;
        }
        Map<String, List<Order>> groups = new LinkedHashMap<>();
        for (Order order : allEligible) {
            groups.computeIfAbsent(pickupOriginKey(order), ignored -> new ArrayList<>()).add(order);
        }
        if (groups.size() == 1) {
            return allEligible;
        }
        // If the lead order is URGENT, always prioritize its warehouse group
        // immediately
        List<Order> firstGroup = groups.values().iterator().next();
        if (firstGroup.get(0).getPriority() == com.swiftroute.orderservice.entity.enums.OrderPriority.URGENT) {
            return firstGroup;
        }
        // Otherwise, if a warehouse group has reached the batch size threshold, select
        // it
        int batchThreshold = (tenant.getDispatchConfig() != null
                && tenant.getDispatchConfig().getBatchSizeThreshold() != null)
                        ? tenant.getDispatchConfig().getBatchSizeThreshold()
                        : 5;
        for (List<Order> group : groups.values()) {
            if (group.size() >= batchThreshold) {
                return group;
            }
        }
        return firstGroup;
    }

    private String pickupOriginKey(Order order) {
        if (order.getPickupLocation() != null && order.getPickupLocation().getId() != null) {
            return order.getPickupLocation().getId().toString();
        }
        double lat = order.getPickupLat() != null ? order.getPickupLat() : 0.0;
        double lng = order.getPickupLng() != null ? order.getPickupLng() : 0.0;
        return String.format(java.util.Locale.ROOT, "%.5f:%.5f", lat, lng);
    }

    private LocationPoint resolveDepot(Tenant tenant, List<Order> orders) {
        Order ref = (orders != null && !orders.isEmpty()) ? orders.get(0) : null;
        if (ref != null && ref.getPickupLocation() != null) {
            var loc = ref.getPickupLocation();
            if (loc.getLat() != null && loc.getLng() != null) {
                return new LocationPoint(
                        loc.getLat(),
                        loc.getLng(),
                        loc.getAddress() != null && !loc.getAddress().isBlank()
                                ? loc.getAddress()
                                : tenant.getAddress());
            }
        }
        Double lat = (ref != null && ref.getPickupLat() != null) ? ref.getPickupLat()
                : (tenant.getPickupLat() != null ? tenant.getPickupLat() : 10.7769);
        Double lng = (ref != null && ref.getPickupLng() != null) ? ref.getPickupLng()
                : (tenant.getPickupLng() != null ? tenant.getPickupLng() : 106.7009);
        String address = (ref != null && ref.getPickupAddress() != null && !ref.getPickupAddress().isBlank())
                ? ref.getPickupAddress()
                : tenant.getAddress();
        return new LocationPoint(lat, lng, address);
    }

    private LocationPoint resolvePlanOrigin(DispatchPlan plan, Double driverLat, Double driverLng) {
        LocationPoint depot = resolveDepot(plan.getTenant(), plan.getOrdersInStopOrder());
        return new LocationPoint(
                driverLat != null ? driverLat : depot.lat(),
                driverLng != null ? driverLng : depot.lng(),
                depot.address());
    }

    private BatchOptimizationRequest buildRoutingRequest(
            Tenant tenant,
            List<Order> orders,
            int vehicleCount,
            int maxStopsPerVehicle) {
        LocationPoint depot = resolveDepot(tenant, orders);

        List<StopItem> stops = orders.stream().map(o -> new StopItem(
                o.getId().toString(),
                o.getRecipientName(),
                new LocationPoint(o.getDeliveryLat(), o.getDeliveryLng(), o.getDeliveryAddress()),
                o.getWeightKg() != null ? o.getWeightKg() : 0.0,
                o.getTimeWindowStart() != null ? o.getTimeWindowStart().toInstant().toString() : null,
                o.getTimeWindowEnd() != null ? o.getTimeWindowEnd().toInstant().toString() : null)).toList();

        double timeWeight = 0.5;
        double costWeight = 0.5;
        if (tenant.getOptimizationWeights() != null) {
            if (tenant.getOptimizationWeights().getTimeWeight() != null) {
                timeWeight = tenant.getOptimizationWeights().getTimeWeight();
            }
            if (tenant.getOptimizationWeights().getCostWeight() != null) {
                costWeight = tenant.getOptimizationWeights().getCostWeight();
            }
        }

        return new BatchOptimizationRequest(
                depot,
                stops,
                "auto",
                timeWeight,
                costWeight,
                Math.max(1, vehicleCount),
                Math.max(1, maxStopsPerVehicle));
    }

    private String serializeStopWaypoints(List<BatchOptimizationResponse.OrderedStop> stops) {
        try {
            return objectMapper.writeValueAsString(stops != null ? stops : List.of());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize route waypoints: {}", e.getMessage());
            return "[]";
        }
    }

    private String serializeOrdersAsWaypoints(List<Order> orders) {
        try {
            List<Map<String, Object>> stops = new ArrayList<>();
            int idx = 0;
            for (Order o : orders) {
                stops.add(Map.of(
                        "stop_index", idx++,
                        "stop_id", o.getId().toString(),
                        "location", Map.of(
                                "lat", o.getDeliveryLat() != null ? o.getDeliveryLat() : 10.7769,
                                "lng", o.getDeliveryLng() != null ? o.getDeliveryLng() : 106.7009,
                                "address", o.getDeliveryAddress() != null ? o.getDeliveryAddress() : "")));
            }
            return objectMapper.writeValueAsString(stops);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private double estimateRouteDistanceKm(Tenant tenant, List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return 0.0;
        }
        LocationPoint depot = resolveDepot(tenant, orders);
        double totalKm = 0.0;
        double prevLat = depot.lat();
        double prevLng = depot.lng();
        for (Order o : orders) {
            double curLat = o.getDeliveryLat() != null ? o.getDeliveryLat() : prevLat;
            double curLng = o.getDeliveryLng() != null ? o.getDeliveryLng() : prevLng;
            totalKm += haversineKm(prevLat, prevLng, curLat, curLng);
            prevLat = curLat;
            prevLng = curLng;
        }
        return Math.round(totalKm * 100.0) / 100.0;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void publishOutboxEvent(DispatchPlan plan, String eventType) {
        OutboxEvent event = OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("DispatchPlan")
                .aggregateId(plan.getId())
                .eventType(eventType)
                .payload(buildPlanEventPayload(plan))
                .build();
        outboxEventRepository.save(event);
    }

    private Object buildPlanEventPayload(DispatchPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plan_id", plan.getId().toString());
        payload.put("tenant_id", plan.getTenant().getId().toString());
        payload.put("plan_status", plan.getPlanStatus().name());
        payload.put("driver_id", plan.getDriverId() != null ? plan.getDriverId().toString() : null);
        payload.put("total_distance_km", plan.getTotalDistanceKm());
        payload.put("estimated_duration_minutes", plan.getEstimatedDurationMinutes());
        payload.put("order_count", plan.getOrders().size());
        return payload;
    }
}
