package com.swiftroute.orderservice.service.planning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.client.RoutingEngineClient;
import com.swiftroute.orderservice.dto.request.ManualAssignmentRequest;
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
import com.swiftroute.orderservice.entity.DispatchPlanOrder;
import com.swiftroute.orderservice.entity.DispatchPlanOrder.DispatchPlanOrderId;
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
 * <li>Collect eligible PAID orders for a tenant.</li>
 * <li>Build a {@link BatchOptimizationRequest} and call the routing
 * engine.</li>
 * <li>Persist the resulting {@link DispatchPlan} in DRAFT state.</li>
 * <li>Auto-approve (if {@code require_approval=false}) and dispatch the
 * plan.</li>
 * <li>Update order statuses to PLANNED, create delivery tasks, and publish
 * outbox events.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchOrchestrationService {

    private static final int DEFAULT_ACK_TIMEOUT_MINUTES = 5;
    private static final int MAX_REASSIGNMENT_ATTEMPTS = 3;

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
            if (order.getStatus() != OrderStatus.PAID || order.getPlanId() != null)
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
                plan.getPlanOrders().add(DispatchPlanOrder.builder()
                        .id(new DispatchPlanOrderId(plan.getId(), order.getId()))
                        .plan(plan).order(order).stopIndex(index++).build());
                order.setPlanId(plan.getId());
                order.setStatus(OrderStatus.PLANNED);
                order.setFulfillmentStatus("PLANNED");
            }
            plan.approve(operatorId);
            dispatchPlan(plan);
        }
    }

    /**
     * Executes a full multi-driver CVRP planning cycle for the given tenant:
     * collect → optimize across available fleet → persist plans → (auto-approve) →
     * dispatch.
     *
     * @param tenantId    UUID of the tenant
     * @param triggerType reason the planning cycle was triggered
     * @return the primary persisted {@link DispatchPlan}, or empty if planning was
     *         skipped
     */
    @Transactional
    public Optional<DispatchPlan> executePlanningCycle(UUID tenantId, PlanTriggerType triggerType) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        Instant now = Instant.now();
        List<Order> allEligible = eligibilityEvaluator.getEligibleOrders(tenantId, now);

        if (allEligible.isEmpty()) {
            log.info("Tenant {} planning cycle skipped — no eligible orders.", tenantId);
            return Optional.empty();
        }

        int maxOrdersPerDriver = (tenant.getDispatchConfig() != null
                && tenant.getDispatchConfig().getMaxOrdersPerDriver() != null)
                        ? tenant.getDispatchConfig().getMaxOrdersPerDriver()
                        : 5;

        // --- Select all available candidate drivers from active fleet ---
        List<UUID> availableDrivers = selectAvailableDrivers(tenantId);
        List<UUID> remainingDrivers = new ArrayList<>(availableDrivers);
        if (remainingDrivers.isEmpty()) {
            log.warn("Tenant {} planning cycle skipped — no available drivers; {} eligible orders remain in PAID.",
                    tenantId, allEligible.size());
            return Optional.empty();
        }

        boolean requiresApproval = Boolean.TRUE.equals(
                tenant.getDispatchConfig() != null && tenant.getDispatchConfig().getRequireApproval());

        // When triggered manually ("Chạy lập kế hoạch ngay"), process ALL warehouse
        // groups
        // in a single run so no depot's pending orders are left behind.
        // For background triggers (BATCH_SIZE, MAX_WAIT, URGENT), process the
        // qualifying group.
        List<List<Order>> warehouseGroups = selectTargetWarehouseGroups(tenant, allEligible, triggerType);
        List<DispatchPlan> createdPlans = new ArrayList<>();

        for (List<Order> warehouseGroup : warehouseGroups) {
            if (warehouseGroup.isEmpty()) {
                continue;
            }
            if (remainingDrivers.isEmpty()) {
                log.warn("Tenant {} stopped planning remaining warehouse group ({} orders) — no remaining free drivers; orders remain in PAID.",
                        tenantId, warehouseGroup.size());
                break;
            }

            int driverCapacityCount = remainingDrivers.size();
            int maxBatchOrders = maxOrdersPerDriver * driverCapacityCount;

            List<Order> eligible = warehouseGroup.size() > maxBatchOrders
                    ? warehouseGroup.subList(0, maxBatchOrders)
                    : warehouseGroup;

            int neededVehicles = (int) Math.ceil((double) eligible.size() / maxOrdersPerDriver);
            int vehicleCount = Math.max(1, Math.min(driverCapacityCount, neededVehicles));

            log.info(
                    "Tenant {} starting planning cycle for warehouse group. Trigger={} EligibleOrders={} RemainingDrivers={} VehicleCount={}",
                    tenantId, triggerType, eligible.size(), remainingDrivers.size(), vehicleCount);

            // --- Build routing engine request (single-vehicle TSP or multi-vehicle CVRP)
            // ---
            BatchOptimizationRequest routingRequest = buildRoutingRequest(
                    tenant, eligible, vehicleCount, maxOrdersPerDriver);

            // --- Call routing engine ---
            Optional<BatchOptimizationResponse> routingResult = routingEngineClient.optimizeBatch(routingRequest);
            if (routingResult.isEmpty()) {
                log.error("Tenant {} planning cycle skipped warehouse group — routing engine returned no result.",
                        tenantId);
                continue;
            }
            BatchOptimizationResponse routingResponse = routingResult.get();

            List<BatchOptimizationResponse.VehicleRoute> vehicleRoutes = (routingResponse.vehicleRoutes() != null
                    && !routingResponse.vehicleRoutes().isEmpty())
                            ? routingResponse.vehicleRoutes().stream()
                                    .filter(vr -> vr.orderedStops() != null && !vr.orderedStops().isEmpty())
                                    .toList()
                            : List.of(new BatchOptimizationResponse.VehicleRoute(
                                    0,
                                    routingResponse.totalDistanceKm(),
                                    routingResponse.totalDurationMinutes(),
                                    0.0,
                                    routingResponse.orderedStops()));

            for (int i = 0; i < vehicleRoutes.size(); i++) {
                BatchOptimizationResponse.VehicleRoute vRoute = vehicleRoutes.get(i);
                if (remainingDrivers.isEmpty()) {
                    log.warn("Tenant {} skipped extra route {} ({} stops) — no remaining free drivers; orders remain in PAID.",
                            tenantId, i, vRoute.orderedStops() != null ? vRoute.orderedStops().size() : 0);
                    break;
                }
                UUID assignedDriverId = remainingDrivers.remove(0);

                DispatchPlan plan = DispatchPlan.builder()
                        .tenant(tenant)
                        .planStatus(PlanStatus.DRAFT)
                        .driverId(assignedDriverId)
                        .algorithmUsed(routingResponse.algorithmUsed())
                        .totalDistanceKm(BigDecimal.valueOf(vRoute.totalDistanceKm()))
                        .estimatedDurationMinutes((int) Math.ceil(vRoute.totalDurationMinutes()))
                        .triggerType(triggerType)
                        .requiresApproval(requiresApproval)
                        .plannedAt(now)
                        .routeWaypoints(serializeStopWaypoints(vRoute.orderedStops()))
                        .build();

                DispatchPlan savedPlan = dispatchPlanRepository.save(plan);

                List<DispatchPlanOrder> planOrders = new ArrayList<>();
                List<Order> routeOrders = new ArrayList<>();
                for (BatchOptimizationResponse.OrderedStop stop : vRoute.orderedStops()) {
                    UUID orderId = UUID.fromString(stop.stopId());
                    eligible.stream()
                            .filter(o -> o.getId().equals(orderId))
                            .findFirst()
                            .ifPresent(order -> {
                                planOrders.add(DispatchPlanOrder.builder()
                                        .id(new DispatchPlanOrderId(savedPlan.getId(), orderId))
                                        .plan(savedPlan)
                                        .order(order)
                                        .stopIndex(stop.stopIndex())
                                        .build());
                                routeOrders.add(order);
                            });
                }
                savedPlan.getPlanOrders().addAll(planOrders);

                UUID currentPlanId = savedPlan.getId();
                routeOrders.forEach(o -> {
                    o.setStatus(OrderStatus.PLANNED);
                    o.setFulfillmentStatus("PLANNED");
                    o.setPlanId(currentPlanId);
                    o.setUpdatedAt(now);
                });
                orderRepository.saveAll(routeOrders);

                DispatchPlan persistedPlan = dispatchPlanRepository.save(savedPlan);
                log.info("Tenant {} dispatch plan {} saved in DRAFT for driver {}. Orders={}",
                        tenantId, persistedPlan.getId(), assignedDriverId, routeOrders.size());

                if (!requiresApproval) {
                    persistedPlan.approve(null);
                    persistedPlan = dispatchPlanRepository.save(persistedPlan);
                    log.info("Tenant {} plan {} auto-approved.", tenantId, persistedPlan.getId());

                    persistedPlan = dispatchPlan(persistedPlan);
                }
                createdPlans.add(persistedPlan);
            }
        }

        return createdPlans.stream().findFirst();
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

        for (DispatchPlanOrder po : plan.getPlanOrders()) {
            Order order = po.getOrder();
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
     * Cancels a plan from any non-terminal state.
     *
     * @param planId UUID of the plan
     * @param reason cancellation reason
     * @return cancelled plan
     */
    @Transactional
    public DispatchPlan cancelPlan(UUID planId, String reason) {
        DispatchPlan plan = loadPlan(planId);
        try {
            plan.cancel(reason);
        } catch (IllegalStateException ex) {
            throw new AppException(ErrorCode.PLAN_ALREADY_CANCELLED);
        }

        plan.getPlanOrders().forEach(po -> {
            Order o = po.getOrder();
            o.setStatus(OrderStatus.PAID);
            o.setFulfillmentStatus("READY_FOR_PLANNING");
            o.setPlanId(null);
            o.setAssignedDriverId(null);
            orderRepository.save(o);
        });

        DispatchPlan saved = dispatchPlanRepository.save(plan);
        publishOutboxEvent(saved, "plan.cancelled");
        log.info("Plan {} cancelled. Reason={}", planId, reason);
        return saved;
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

        for (DispatchPlanOrder po : plan.getPlanOrders()) {
            deliveryTaskRepository.findByOrder_IdAndTaskType(po.getOrder().getId(), "DELIVERY")
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
     * Returns affected orders to PAID and cancels the plan.
     */
    @Transactional
    public DispatchPlan declineByDriver(UUID planId, String reason) {
        DispatchPlan plan = loadPlan(planId);
        try {
            plan.decline(reason);
        } catch (IllegalStateException ex) {
            throw new AppException(ErrorCode.INVALID_PLAN_STATUS);
        }

        plan.getPlanOrders().forEach(po -> {
            Order o = po.getOrder();
            o.setStatus(OrderStatus.PAID);
            o.setFulfillmentStatus("READY_FOR_PLANNING");
            o.setPlanId(null);
            o.setAssignedDriverId(null);
            orderRepository.save(o);
        });

        plan.cancel("Driver declined: " + reason);
        DispatchPlan saved = dispatchPlanRepository.save(plan);
        publishOutboxEvent(saved, "plan.cancelled");
        return saved;
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
        if (hasClaimedDeliveryExecution(plan)) {
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

            for (DispatchPlanOrder po : plan.getPlanOrders()) {
                Order order = po.getOrder();
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

        // Max retries reached or no alternative driver available -> cancel & escalate
        plan.getPlanOrders().forEach(po -> {
            Order o = po.getOrder();
            o.setStatus(OrderStatus.PAID);
            o.setFulfillmentStatus("READY_FOR_PLANNING");
            o.setPlanId(null);
            o.setAssignedDriverId(null);
            orderRepository.save(o);
        });
        plan.cancel("Acknowledgement timeout exceeded max reassignment attempts (" + nextCount + ")");
        DispatchPlan saved = dispatchPlanRepository.save(plan);
        // Do not spam escalation alert to notification inbox
        log.warn("Plan {} escalated after {} reassignment attempts.", planId, nextCount);
        return saved;
    }

    private boolean hasClaimedDeliveryExecution(DispatchPlan plan) {
        return plan.getPlanOrders().stream()
                .map(DispatchPlanOrder::getOrder)
                .map(Order::getId)
                .map(orderId -> deliveryTaskRepository.findByOrder_IdAndTaskType(orderId, "DELIVERY"))
                .flatMap(Optional::stream)
                .map(DeliveryTask::getStatus)
                .anyMatch(status -> status != DeliveryTaskStatus.ASSIGNED
                        && status != DeliveryTaskStatus.CANCELLED);
    }

    /**
     * Dynamically inserts a new order into an active DISPATCHED plan using OR-Tools POST /optimize/insert while preserving completed stops (P3.4).
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
        newOrder.setFulfillmentStatus("PLANNED");
        newOrder.setPlanId(plan.getId());
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
        return plan.getPlanOrders().stream()
                .sorted((a, b) -> Integer.compare(a.getStopIndex(), b.getStopIndex()))
                .map(po -> {
                    Order o = po.getOrder();
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
        Map<UUID, Integer> indexByOrderId = new LinkedHashMap<>();
        for (BatchOptimizationResponse.OrderedStop stop : orderedStops) {
            indexByOrderId.put(UUID.fromString(stop.stopId()), stop.stopIndex());
        }

        for (DispatchPlanOrder po : plan.getPlanOrders()) {
            Integer newIdx = indexByOrderId.get(po.getOrder().getId());
            if (newIdx != null) {
                po.setStopIndex(newIdx);
            }
        }

        if (newlyInsertedOrder != null
                && plan.getPlanOrders().stream()
                        .noneMatch(po -> po.getOrder().getId().equals(newlyInsertedOrder.getId()))) {
            int stopIdx = indexByOrderId.getOrDefault(newlyInsertedOrder.getId(), plan.getPlanOrders().size() + 1);
            plan.getPlanOrders().add(DispatchPlanOrder.builder()
                    .id(new DispatchPlanOrderId(plan.getId(), newlyInsertedOrder.getId()))
                    .plan(plan)
                    .order(newlyInsertedOrder)
                    .stopIndex(stopIdx)
                    .build());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private UUID selectCandidateDriver(UUID tenantId) {
        return selectAlternativeDriver(tenantId, null);
    }

    private List<UUID> selectAvailableDrivers(UUID tenantId) {
        List<UUID> allFleetDrivers = new ArrayList<>();
        try {
            OperationalReadinessService.FleetState fleet = operationalReadinessService.fleetState(tenantId);
            if (fleet != null && fleet.available() && fleet.drivers() != null) {
                for (Map<String, Object> driver : fleet.drivers()) {
                    Object rawId = driver.get("user_id") != null ? driver.get("user_id") : driver.get("driver_id");
                    if (rawId != null) {
                        UUID candidate = UUID.fromString(String.valueOf(rawId));
                        if (!allFleetDrivers.contains(candidate)) {
                            allFleetDrivers.add(candidate);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Could not fetch available drivers for tenant {}: {}", tenantId, ex.getMessage());
        }
        if (allFleetDrivers.isEmpty()) {
            return allFleetDrivers;
        }
        java.util.Set<UUID> busyDriverIds = new java.util.HashSet<>();
        dispatchPlanRepository.findAllByTenantIdAndPlanStatusIn(
                tenantId, List.of(PlanStatus.APPROVED, PlanStatus.DISPATCHED))
                .stream()
                .map(DispatchPlan::getDriverId)
                .filter(java.util.Objects::nonNull)
                .forEach(busyDriverIds::add);

        List<UUID> freeDrivers = allFleetDrivers.stream()
                .filter(driverId -> !busyDriverIds.contains(driverId))
                .toList();
        return !freeDrivers.isEmpty() ? freeDrivers : allFleetDrivers;
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
        List<Order> planOrders = plan.getPlanOrders().stream()
                .sorted((a, b) -> Integer.compare(a.getStopIndex(), b.getStopIndex()))
                .map(DispatchPlanOrder::getOrder)
                .toList();
        LocationPoint depot = resolveDepot(plan.getTenant(), planOrders);
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
            int idx = 1;
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
        payload.put("order_count", plan.getPlanOrders().size());
        return payload;
    }
}
