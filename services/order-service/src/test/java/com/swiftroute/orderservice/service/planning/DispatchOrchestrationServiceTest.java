package com.swiftroute.orderservice.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.client.RoutingEngineClient;
import com.swiftroute.orderservice.dto.request.MovePlanStopRequest;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse;
import com.swiftroute.orderservice.dto.request.ReorderPlanStopsRequest;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse.LocationPoint;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse.OrderedStop;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.entity.enums.PlanTriggerType;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.TenantRepository;
import com.swiftroute.orderservice.service.DeliveryExecutionService;
import com.swiftroute.orderservice.service.OperationalReadinessService;

@ExtendWith(MockitoExtension.class)
class DispatchOrchestrationServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DispatchPlanRepository dispatchPlanRepository;
    @Mock
    private DeliveryTaskRepository deliveryTaskRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private RoutingEngineClient routingEngineClient;
    @Mock
    private OperationalReadinessService operationalReadinessService;
    @Mock
    private DeliveryExecutionService deliveryExecutionService;

    private PlanningEligibilityEvaluator eligibilityEvaluator;
    private DispatchOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        eligibilityEvaluator = new PlanningEligibilityEvaluator(orderRepository);
        orchestrationService = new DispatchOrchestrationService(
                tenantRepository,
                orderRepository,
                dispatchPlanRepository,
                deliveryTaskRepository,
                outboxEventRepository,
                routingEngineClient,
                eligibilityEvaluator,
                operationalReadinessService,
                deliveryExecutionService,
                new ObjectMapper());
    }

    @Test
    void evaluate_shouldTriggerOnUrgentOrder() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order urgentOrder = buildOrder(tenant, OrderPriority.URGENT, Instant.now());

        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.READY_FOR_DISPATCH))
                .thenReturn(List.of(urgentOrder));

        Optional<PlanTriggerType> trigger = eligibilityEvaluator.evaluate(tenant, Instant.now());
        assertThat(trigger).contains(PlanTriggerType.URGENT);
    }

    @Test
    void evaluate_shouldTriggerOnMaxWaitTime() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order oldOrder = buildOrder(tenant, OrderPriority.NORMAL, Instant.now().minusSeconds(400));

        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.READY_FOR_DISPATCH))
                .thenReturn(List.of(oldOrder));

        Optional<PlanTriggerType> trigger = eligibilityEvaluator.evaluate(tenant, Instant.now());
        assertThat(trigger).contains(PlanTriggerType.MAX_WAIT);
    }

    @Test
    void executePlanningCycle_shouldCreateDraftPlanWithoutAssigningDriver() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.READY_FOR_DISPATCH))
                .thenReturn(List.of(order));

        OrderedStop stop = new OrderedStop(
                0, order.getId().toString(), new LocationPoint(10.77, 106.70, "Addr"), 3.2, 8.5);
        BatchOptimizationResponse routingResponse = new BatchOptimizationResponse(
                "job-1", "ortools", 3.2, 8.5, List.of(stop), 12.0, Instant.now().toString());
        when(routingEngineClient.optimizeBatch(any())).thenReturn(Optional.of(routingResponse));

        when(dispatchPlanRepository.save(any(DispatchPlan.class))).thenAnswer(inv -> {
            DispatchPlan p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        Optional<DispatchPlan> result = orchestrationService.executePlanningCycle(
                tenantId, PlanTriggerType.BATCH_SIZE);

        assertThat(result).isPresent();
        DispatchPlan plan = result.get();
        assertThat(plan.getPlanStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(plan.getDriverId()).isNull();
        assertThat(plan.getAlgorithmUsed()).isEqualTo("ortools");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(order.getPlanId()).isEqualTo(plan.getId());
        assertThat(order.getAssignedDriverId()).isNull();
        verifyNoInteractions(operationalReadinessService);
        verifyNoInteractions(deliveryExecutionService);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void executePlanningCycle_shouldUseOrderPickupLocationAsDepotAndGroupSameWarehouse() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);

        com.swiftroute.orderservice.entity.PickupLocation warehouseB = com.swiftroute.orderservice.entity.PickupLocation.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Kho B Thu Duc")
                .address("789 Vo Van Ngan, Thu Duc")
                .lat(10.8505)
                .lng(106.7718)
                .build();

        Order warehouseBOrder = buildOrder(tenant, OrderPriority.URGENT, Instant.now());
        warehouseBOrder.setPickupLocation(warehouseB);
        warehouseBOrder.setPickupLat(10.8505);
        warehouseBOrder.setPickupLng(106.7718);
        warehouseBOrder.setPickupAddress("789 Vo Van Ngan, Thu Duc");

        Order warehouseAOrder = buildOrder(tenant, OrderPriority.NORMAL, Instant.now().plusSeconds(10));

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.READY_FOR_DISPATCH))
                .thenReturn(List.of(warehouseBOrder, warehouseAOrder));

        OrderedStop stop = new OrderedStop(
                0, warehouseBOrder.getId().toString(), new LocationPoint(10.77, 106.70, "Addr"), 3.2, 8.5);
        BatchOptimizationResponse routingResponse = new BatchOptimizationResponse(
                "job-2", "ortools", 3.2, 8.5, List.of(stop), 12.0, Instant.now().toString());
        org.mockito.ArgumentCaptor<com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest.class);
        when(routingEngineClient.optimizeBatch(requestCaptor.capture())).thenReturn(Optional.of(routingResponse));

        when(dispatchPlanRepository.save(any(DispatchPlan.class))).thenAnswer(inv -> {
            DispatchPlan p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        Optional<DispatchPlan> result = orchestrationService.executePlanningCycle(
                tenantId, PlanTriggerType.URGENT);

        assertThat(result).isPresent();
        com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.depot().lat()).isEqualTo(10.8505);
        assertThat(sentRequest.depot().lng()).isEqualTo(106.7718);
        assertThat(sentRequest.depot().address()).isEqualTo("789 Vo Van Ngan, Thu Duc");
        assertThat(sentRequest.stops()).hasSize(1);
        assertThat(sentRequest.stops().get(0).id()).isEqualTo(warehouseBOrder.getId().toString());
        assertThat(warehouseAOrder.getStatus()).isEqualTo(OrderStatus.READY_FOR_DISPATCH);
    }

    @Test
    void executePlanningCycle_manualTriggerShouldPlanAllWarehouseGroupsAsDraftWithoutDrivers() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);

        com.swiftroute.orderservice.entity.PickupLocation warehouseB = com.swiftroute.orderservice.entity.PickupLocation.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Kho Bnk1")
                .address("14 3 Thang 2, Xuan Khanh")
                .lat(10.0320)
                .lng(105.7743)
                .build();

        Order warehouseAOrder1 = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        Order warehouseAOrder2 = buildOrder(tenant, OrderPriority.NORMAL, Instant.now().plusSeconds(5));
        Order warehouseBOrder = buildOrder(tenant, OrderPriority.NORMAL, Instant.now().plusSeconds(10));
        warehouseBOrder.setPickupLocation(warehouseB);
        warehouseBOrder.setPickupLat(10.0320);
        warehouseBOrder.setPickupLng(105.7743);
        warehouseBOrder.setPickupAddress("14 3 Thang 2, Xuan Khanh");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.READY_FOR_DISPATCH))
                .thenReturn(List.of(warehouseAOrder1, warehouseAOrder2, warehouseBOrder));

        when(routingEngineClient.optimizeBatch(any())).thenAnswer(inv -> {
            com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest req = inv.getArgument(0);
            List<OrderedStop> stops = new java.util.ArrayList<>();
            for (int i = 0; i < req.stops().size(); i++) {
                var s = req.stops().get(i);
                stops.add(new OrderedStop(
                        i + 1,
                        s.id(),
                        new LocationPoint(s.location().lat(), s.location().lng(), s.location().address()),
                        2.0,
                        5.0));
            }
            return Optional.of(new BatchOptimizationResponse(
                    UUID.randomUUID().toString(), "ortools", 4.0, 10.0, stops, 12.0, Instant.now().toString()));
        });

        when(dispatchPlanRepository.save(any(DispatchPlan.class))).thenAnswer(inv -> {
            DispatchPlan p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        Optional<DispatchPlan> result = orchestrationService.executePlanningCycle(
                tenantId, PlanTriggerType.MANUAL);

        assertThat(result).isPresent();
        assertThat(result.get().getPlanStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(result.get().getDriverId()).isNull();
        assertThat(warehouseAOrder1.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(warehouseAOrder2.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(warehouseBOrder.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(warehouseAOrder1.getAssignedDriverId()).isNull();
        assertThat(warehouseAOrder2.getAssignedDriverId()).isNull();
        assertThat(warehouseBOrder.getAssignedDriverId()).isNull();
        assertThat(warehouseAOrder1.getPlanId()).isNotEqualTo(warehouseBOrder.getPlanId());
    }

    @Test
    void executePlanningCycle_whenNoDriversOnline_shouldStillCreateDraftPlans() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order1 = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        Order order2 = buildOrder(tenant, OrderPriority.NORMAL, Instant.now().plusSeconds(5));

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.READY_FOR_DISPATCH))
                .thenReturn(List.of(order1, order2));

        OrderedStop stop1 = new OrderedStop(
                0, order1.getId().toString(), new LocationPoint(10.775, 106.701, "Addr 1"), 2.0, 5.0);
        OrderedStop stop2 = new OrderedStop(
                0, order2.getId().toString(), new LocationPoint(10.785, 106.711, "Addr 2"), 3.0, 7.0);
        BatchOptimizationResponse routingResponse = new BatchOptimizationResponse(
                "job-split",
                "ortools",
                5.0,
                12.0,
                List.of(stop1, stop2),
                List.of(
                        new BatchOptimizationResponse.VehicleRoute(0, 2.0, 5.0, 2.0, List.of(stop1)),
                        new BatchOptimizationResponse.VehicleRoute(1, 3.0, 7.0, 2.0, List.of(stop2))),
                12.0,
                Instant.now().toString());
        when(routingEngineClient.optimizeBatch(any())).thenReturn(Optional.of(routingResponse));

        when(dispatchPlanRepository.save(any(DispatchPlan.class))).thenAnswer(inv -> {
            DispatchPlan p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        Optional<DispatchPlan> result = orchestrationService.executePlanningCycle(
                tenantId, PlanTriggerType.BATCH_SIZE);

        assertThat(result).isPresent();
        assertThat(result.get().getPlanStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(order1.getPlanId()).isNotNull();
        assertThat(order2.getPlanId()).isNotNull();
        assertThat(order1.getPlanId()).isNotEqualTo(order2.getPlanId());
        verifyNoInteractions(operationalReadinessService);
    }

    @Test
    void executePlanningCycle_whenRoutingReturnsDuplicateStops_shouldSkipGroupWithoutSaving() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.READY_FOR_DISPATCH))
                .thenReturn(List.of(order));

        OrderedStop dup1 = new OrderedStop(
                0, order.getId().toString(), new LocationPoint(10.77, 106.70, "Addr"), 1.0, 3.0);
        OrderedStop dup2 = new OrderedStop(
                1, order.getId().toString(), new LocationPoint(10.77, 106.70, "Addr"), 2.0, 6.0);
        when(routingEngineClient.optimizeBatch(any())).thenReturn(Optional.of(new BatchOptimizationResponse(
                "job-dup", "ortools", 2.0, 6.0, List.of(dup1, dup2), 8.0, Instant.now().toString())));

        Optional<DispatchPlan> result = orchestrationService.executePlanningCycle(
                tenantId, PlanTriggerType.BATCH_SIZE);

        assertThat(result).isEmpty();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_DISPATCH);
        assertThat(order.getPlanId()).isNull();
        verify(dispatchPlanRepository, never()).save(any());
    }

    @Test
    void reorderingAnUnassignedDraftPersistsTheRequestedSequenceAndUpdatedMetrics() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        DispatchPlan plan = DispatchPlan.builder().id(planId).tenant(tenant).version(4L).build();
        Order first = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        Order second = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        plan.addOrder(first, 1);
        plan.addOrder(second, 2);
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(dispatchPlanRepository.saveAndFlush(plan)).thenReturn(plan);
        List<UUID> sequence = List.of(second.getId(), first.getId());
        when(routingEngineClient.measureOrderedRoute(any())).thenAnswer(invocation -> {
            var request = invocation.<com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest>getArgument(0);
            assertThat(request.stops()).extracting(stop -> stop.id())
                    .containsExactly(second.getId().toString(), first.getId().toString());
            return Optional.of(new BatchOptimizationResponse("job", "manual", 5.2, 11.4,
                    List.of(
                            new OrderedStop(1, second.getId().toString(), new LocationPoint(10.1, 105.7, "B"), 2, 5),
                            new OrderedStop(2, first.getId().toString(), new LocationPoint(10.2, 105.7, "A"), 5.2, 11.4)),
                    4, Instant.now().toString()));
        });

        orchestrationService.reorderDraftStops(tenantId, planId, new ReorderPlanStopsRequest(4L, sequence));

        assertThat(first.getPlanStopIndex()).isEqualTo(2);
        assertThat(second.getPlanStopIndex()).isEqualTo(1);
        var waypoints = new ObjectMapper().readTree(plan.getRouteWaypoints());
        assertThat(waypoints.get(0).get("stop_id").asText()).isEqualTo(second.getId().toString());
        assertThat(waypoints.get(1).get("stop_id").asText()).isEqualTo(first.getId().toString());
        assertThat(waypoints.get(1).get("estimated_arrival_minutes").asDouble()).isEqualTo(11.4);
        assertThat(plan.getTotalDistanceKm()).isEqualByComparingTo("5.2");
        assertThat(plan.getEstimatedDurationMinutes()).isEqualTo(12);
        assertThat(plan.getDriverId()).isNull();
        verifyNoInteractions(deliveryExecutionService, outboxEventRepository);
    }

    @Test
    void reorderingRejectsStaleEditsAndAlreadyAssignedPlansBeforeMeasuring() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        DispatchPlan plan = DispatchPlan.builder().id(planId).tenant(buildTenant(tenantId)).version(4L).build();
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        var ids = List.of(UUID.randomUUID());

        assertThatThrownBy(() -> orchestrationService.reorderDraftStops(tenantId, planId,
                new ReorderPlanStopsRequest(3L, ids)))
                .isInstanceOf(com.swiftroute.orderservice.exception.AppException.class)
                .extracting("errorCode")
                .isEqualTo(com.swiftroute.orderservice.exception.ErrorCode.CONCURRENT_UPDATE);

        plan.setDriverId(UUID.randomUUID());
        assertThatThrownBy(() -> orchestrationService.reorderDraftStops(tenantId, planId,
                new ReorderPlanStopsRequest(4L, ids)))
                .isInstanceOf(com.swiftroute.orderservice.exception.AppException.class)
                .extracting("errorCode")
                .isEqualTo(com.swiftroute.orderservice.exception.ErrorCode.INVALID_PLAN_STATUS);
        verifyNoInteractions(routingEngineClient);
    }

    @Test
    void cancelPlanReturnsOrderToQueueAndDiscardsOldStops() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        order.setAssignedDriverId(driverId);
        order.setStatus(OrderStatus.PLANNED);
        DispatchPlan plan = DispatchPlan.builder()
                .id(planId)
                .tenant(tenant)
                .driverId(driverId)
                .planStatus(PlanStatus.DISPATCHED)
                .routeWaypoints("[{}]")
                .build();
        plan.addOrder(order, 0);
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        DispatchPlan cancelled = orchestrationService.cancelPlan(planId, "Replan");

        assertThat(cancelled.getOrders()).isEmpty();
        assertThat(cancelled.getRouteWaypoints()).isEqualTo("[]");
        assertThat(order.canJoinDispatchPlan()).isTrue();
        assertThat(order.getAssignedDriverId()).isNull();
        verify(dispatchPlanRepository).delete(plan);
        verify(outboxEventRepository).save(org.mockito.ArgumentMatchers.argThat(event ->
                "plan.cancelled".equals(event.getEventType())
                        && event.getPayload() instanceof Map<?, ?> payload
                        && Integer.valueOf(1).equals(payload.get("order_count"))));
    }

    @Test
    void insertOrderRejectsAnOrderAlreadyAssignedToAnotherPlan() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID existingPlanId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        DispatchPlan.builder().id(existingPlanId).tenant(tenant).build().addOrder(order, 0);
        DispatchPlan plan = DispatchPlan.builder().id(planId).tenant(tenant)
                .planStatus(PlanStatus.DISPATCHED).build();
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orchestrationService.insertOrderIntoActivePlan(planId, order.getId(), null, null))
                .isInstanceOf(com.swiftroute.orderservice.exception.AppException.class)
                .extracting("errorCode")
                .isEqualTo(com.swiftroute.orderservice.exception.ErrorCode.INVALID_ORDER_STATUS);

        assertThat(order.getPlanId()).isEqualTo(existingPlanId);
        assertThat(plan.getOrders()).isEmpty();
        verifyNoInteractions(routingEngineClient, deliveryExecutionService, outboxEventRepository);
    }

    @Test
    void moveDraftStop_whenTargetPlanReachedMaxOrdersPerDriver_shouldRejectWithoutMeasuring() {
        UUID tenantId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        tenant.getDispatchConfig().setMaxOrdersPerDriver(2);

        Order movedOrder = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        Order targetOrder1 = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        Order targetOrder2 = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());

        DispatchPlan source = DispatchPlan.builder()
                .id(sourceId)
                .tenant(tenant)
                .planStatus(PlanStatus.DRAFT)
                .driverId(null)
                .version(1L)
                .build();
        source.addOrder(movedOrder, 1);

        DispatchPlan target = DispatchPlan.builder()
                .id(targetId)
                .tenant(tenant)
                .planStatus(PlanStatus.DRAFT)
                .driverId(null)
                .version(1L)
                .build();
        target.addOrder(targetOrder1, 1);
        target.addOrder(targetOrder2, 2);

        when(dispatchPlanRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(dispatchPlanRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> orchestrationService.moveDraftStop(
                tenantId,
                new MovePlanStopRequest(sourceId, targetId, movedOrder.getId(), 2, 1L, 1L)))
                .isInstanceOf(com.swiftroute.orderservice.exception.AppException.class)
                .extracting("errorCode")
                .isEqualTo(com.swiftroute.orderservice.exception.ErrorCode.PLAN_MAX_ORDERS_EXCEEDED);

        assertThat(target.getOrders()).hasSize(2);
        assertThat(source.getOrders()).hasSize(1);
        verifyNoInteractions(routingEngineClient);
    }

    @Test
    void insertOrderIntoActivePlan_whenPlanReachedMaxOrdersPerDriver_shouldRejectWithoutCallingRoutingEngine() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        tenant.getDispatchConfig().setMaxOrdersPerDriver(2);

        Order existingOrder1 = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        Order existingOrder2 = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        Order thirdOrder = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());

        DispatchPlan plan = DispatchPlan.builder()
                .id(planId)
                .tenant(tenant)
                .planStatus(PlanStatus.DISPATCHED)
                .driverId(UUID.randomUUID())
                .build();
        plan.addOrder(existingOrder1, 1);
        plan.addOrder(existingOrder2, 2);

        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(orderRepository.findById(thirdOrder.getId())).thenReturn(Optional.of(thirdOrder));

        assertThatThrownBy(() -> orchestrationService.insertOrderIntoActivePlan(
                planId, thirdOrder.getId(), null, null))
                .isInstanceOf(com.swiftroute.orderservice.exception.AppException.class)
                .extracting("errorCode")
                .isEqualTo(com.swiftroute.orderservice.exception.ErrorCode.PLAN_MAX_ORDERS_EXCEEDED);

        assertThat(plan.getOrders()).hasSize(2);
        verifyNoInteractions(routingEngineClient);
    }

    @Test
    void planningRejectsRoutingResultThatPlacesAnOrderOnTwoRoutes() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.READY_FOR_DISPATCH))
                .thenReturn(List.of(order));
        OrderedStop stop = new OrderedStop(
                1, order.getId().toString(), new LocationPoint(10.775, 106.701, "Addr"), 2.0, 5.0);
        when(routingEngineClient.optimizeBatch(any())).thenReturn(Optional.of(new BatchOptimizationResponse(
                "duplicate", "ortools", 4.0, 10.0, List.of(stop, stop),
                List.of(
                        new BatchOptimizationResponse.VehicleRoute(0, 2.0, 5.0, 1.0, List.of(stop)),
                        new BatchOptimizationResponse.VehicleRoute(1, 2.0, 5.0, 1.0, List.of(stop))),
                12.0, Instant.now().toString())));

        assertThat(orchestrationService.executePlanningCycle(tenantId, PlanTriggerType.MANUAL)).isEmpty();
        assertThat(order.getPlanId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_DISPATCH);
        verify(dispatchPlanRepository, never()).save(any());
        verifyNoInteractions(deliveryExecutionService, outboxEventRepository);
    }

    @Test
    void approvePlan_whenDriverMissing_shouldRejectWithOperationsNotReady() {
        UUID planId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        DispatchPlan unassignedDraft = DispatchPlan.builder()
                .id(planId)
                .tenant(tenant)
                .planStatus(PlanStatus.DRAFT)
                .driverId(null)
                .plannedAt(Instant.now())
                .build();
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(unassignedDraft));

        assertThatThrownBy(() -> orchestrationService.approvePlan(planId, UUID.randomUUID(), null))
                .isInstanceOf(com.swiftroute.orderservice.exception.AppException.class)
                .extracting("errorCode")
                .isEqualTo(com.swiftroute.orderservice.exception.ErrorCode.OPERATIONS_NOT_READY);
    }

    @Test
    void acknowledgementTimeout_shouldNotReassignToDriverWithAnotherActivePlan() {
        UUID tenantId = UUID.randomUUID();
        UUID timedOutPlanId = UUID.randomUUID();
        UUID otherPlanId = UUID.randomUUID();
        UUID currentDriverId = UUID.randomUUID();
        UUID busyDriverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);

        DispatchPlan timedOutPlan = DispatchPlan.builder()
                .id(timedOutPlanId)
                .tenant(tenant)
                .driverId(currentDriverId)
                .planStatus(PlanStatus.DISPATCHED)
                .ackDeadlineAt(Instant.now().minusSeconds(60))
                .plannedAt(Instant.now().minusSeconds(600))
                .build();
        DispatchPlan otherActivePlan = DispatchPlan.builder()
                .id(otherPlanId)
                .tenant(tenant)
                .driverId(busyDriverId)
                .planStatus(PlanStatus.DISPATCHED)
                .plannedAt(Instant.now().minusSeconds(600))
                .build();

        when(dispatchPlanRepository.findById(timedOutPlanId)).thenReturn(Optional.of(timedOutPlan));
        when(dispatchPlanRepository.findAllByTenantIdAndPlanStatusIn(
                tenantId, List.of(PlanStatus.APPROVED, PlanStatus.DISPATCHED)))
                .thenReturn(List.of(timedOutPlan, otherActivePlan));
        when(operationalReadinessService.fleetState(tenantId))
                .thenReturn(new OperationalReadinessService.FleetState(true, List.of(
                        Map.of("user_id", currentDriverId.toString()),
                        Map.of("user_id", busyDriverId.toString()))));

        DispatchPlan result = orchestrationService.handleAcknowledgementTimeout(timedOutPlanId);

        assertThat(result.getDriverId()).isEqualTo(currentDriverId);
        verify(dispatchPlanRepository).delete(timedOutPlan);
    }

    @Test
    void acknowledgementTimeout_shouldUseAFreeDriverWhenBusyDriversAreExcluded() {
        UUID tenantId = UUID.randomUUID();
        UUID timedOutPlanId = UUID.randomUUID();
        UUID currentDriverId = UUID.randomUUID();
        UUID busyDriverId = UUID.randomUUID();
        UUID freeDriverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);

        DispatchPlan timedOutPlan = DispatchPlan.builder()
                .id(timedOutPlanId)
                .tenant(tenant)
                .driverId(currentDriverId)
                .planStatus(PlanStatus.DISPATCHED)
                .ackDeadlineAt(Instant.now().minusSeconds(60))
                .plannedAt(Instant.now().minusSeconds(600))
                .build();
        DispatchPlan otherActivePlan = DispatchPlan.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .driverId(busyDriverId)
                .planStatus(PlanStatus.DISPATCHED)
                .plannedAt(Instant.now().minusSeconds(600))
                .build();

        when(dispatchPlanRepository.findById(timedOutPlanId)).thenReturn(Optional.of(timedOutPlan));
        when(dispatchPlanRepository.findAllByTenantIdAndPlanStatusIn(
                tenantId, List.of(PlanStatus.APPROVED, PlanStatus.DISPATCHED)))
                .thenReturn(List.of(timedOutPlan, otherActivePlan));
        when(operationalReadinessService.fleetState(tenantId))
                .thenReturn(new OperationalReadinessService.FleetState(true, List.of(
                        Map.of("user_id", currentDriverId.toString()),
                        Map.of("user_id", busyDriverId.toString()),
                        Map.of("user_id", freeDriverId.toString()))));
        when(dispatchPlanRepository.save(any(DispatchPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchPlan result = orchestrationService.handleAcknowledgementTimeout(timedOutPlanId);

        assertThat(result.getPlanStatus()).isEqualTo(PlanStatus.DISPATCHED);
        assertThat(result.getDriverId()).isEqualTo(freeDriverId);
    }

    @Test
    void acknowledgementTimeout_shouldPreserveRouteWhenDeliveryAlreadyStarted() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now().minusSeconds(600));
        order.setStatus(OrderStatus.IN_TRANSIT);
        order.setAssignedDriverId(driverId);

        DispatchPlan plan = DispatchPlan.builder()
                .id(planId)
                .tenant(tenant)
                .driverId(driverId)
                .planStatus(PlanStatus.DISPATCHED)
                .ackDeadlineAt(Instant.now().minusSeconds(60))
                .plannedAt(Instant.now().minusSeconds(600))
                .build();
        plan.addOrder(order, 0);
        DeliveryTask task = DeliveryTask.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenantId(tenantId)
                .driverId(driverId)
                .dispatchPlanId(planId)
                .status(DeliveryTaskStatus.OUT_FOR_DELIVERY)
                .build();

        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(deliveryTaskRepository.findByOrder_IdAndTaskType(order.getId(), "DELIVERY"))
                .thenReturn(Optional.of(task));
        when(dispatchPlanRepository.save(any(DispatchPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchPlan result = orchestrationService.handleAcknowledgementTimeout(planId);

        assertThat(result.getPlanStatus()).isEqualTo(PlanStatus.DISPATCHED);
        assertThat(result.getAcknowledgedAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
        assertThat(order.getAssignedDriverId()).isEqualTo(driverId);
        assertThat(order.getPlanId()).isEqualTo(planId);
    }

    @Test
    void packageDeliveryTransferMovesOrderTaskAndStopToReceivingDriverPlan() {
        UUID tenantId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID fromDriverId = UUID.randomUUID();
        UUID toDriverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        order.setStatus(OrderStatus.IN_TRANSIT);
        order.setAssignedDriverId(fromDriverId);

        DispatchPlan sourcePlan = DispatchPlan.builder()
                .id(sourcePlanId)
                .tenant(tenant)
                .driverId(fromDriverId)
                .planStatus(PlanStatus.DISPATCHED)
                .build();
        sourcePlan.addOrder(order, 0);
        DeliveryTask task = DeliveryTask.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenantId(tenantId)
                .driverId(fromDriverId)
                .dispatchPlanId(sourcePlanId)
                .status(DeliveryTaskStatus.OUT_FOR_DELIVERY)
                .build();
        AtomicReference<DispatchPlan> createdTarget = new AtomicReference<>();

        when(deliveryTaskRepository.findByOrder_IdAndTaskType(order.getId(), "DELIVERY"))
                .thenReturn(Optional.of(task));
        when(dispatchPlanRepository.findById(sourcePlanId)).thenReturn(Optional.of(sourcePlan));
        when(dispatchPlanRepository.findAllByDriverIdAndPlanStatusIn(
                toDriverId, List.of(PlanStatus.DISPATCHED)))
                .thenReturn(List.of());
        when(dispatchPlanRepository.save(any(DispatchPlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dispatchPlanRepository.saveAndFlush(any(DispatchPlan.class))).thenAnswer(inv -> {
            DispatchPlan plan = inv.getArgument(0);
            plan.setId(UUID.randomUUID());
            createdTarget.set(plan);
            return plan;
        });

        orchestrationService.synchronizePackageTransfer(order, fromDriverId, toDriverId, "DELIVERY");

        assertThat(sourcePlan.getPlanStatus()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(sourcePlan.getOrders()).isEmpty();
        assertThat(createdTarget.get()).isNotNull();
        assertThat(createdTarget.get().getOrders())
                .extracting(Order::getId)
                .containsExactly(order.getId());
        assertThat(order.getAssignedDriverId()).isEqualTo(toDriverId);
        assertThat(order.getPlanId()).isEqualTo(createdTarget.get().getId());
        assertThat(task.getDriverId()).isEqualTo(toDriverId);
        assertThat(task.getDispatchPlanId()).isEqualTo(createdTarget.get().getId());
    }

    @Test
    void packageReturnTransferRemovesDeliveryStopWithoutAddingCustomerStopToReceiverPlan() {
        UUID tenantId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID fromDriverId = UUID.randomUUID();
        UUID toDriverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        order.setStatus(OrderStatus.RETURNING);
        order.setAssignedDriverId(fromDriverId);

        DispatchPlan sourcePlan = DispatchPlan.builder()
                .id(sourcePlanId)
                .tenant(tenant)
                .driverId(fromDriverId)
                .planStatus(PlanStatus.DISPATCHED)
                .build();
        sourcePlan.addOrder(order, 0);
        DeliveryTask returnTask = DeliveryTask.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenantId(tenantId)
                .driverId(fromDriverId)
                .dispatchPlanId(sourcePlanId)
                .taskType("RETURN")
                .status(DeliveryTaskStatus.OUT_FOR_DELIVERY)
                .build();

        when(deliveryTaskRepository.findByOrder_IdAndTaskType(order.getId(), "RETURN"))
                .thenReturn(Optional.of(returnTask));
        when(dispatchPlanRepository.findById(sourcePlanId)).thenReturn(Optional.of(sourcePlan));
        when(dispatchPlanRepository.save(any(DispatchPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestrationService.synchronizePackageTransfer(order, fromDriverId, toDriverId, "RETURN");

        assertThat(sourcePlan.getPlanStatus()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(order.getAssignedDriverId()).isEqualTo(toDriverId);
        assertThat(order.getPlanId()).isNull();
        assertThat(returnTask.getDriverId()).isEqualTo(toDriverId);
        assertThat(returnTask.getDispatchPlanId()).isNull();
        verify(dispatchPlanRepository, never()).findAllByDriverIdAndPlanStatusIn(
                toDriverId, List.of(PlanStatus.DISPATCHED));
    }

    @Test
    void packageReturnTransferPreservesCompletedStatusAndRemovesOldMembership() {
        UUID tenantId = UUID.randomUUID();
        UUID sourcePlanId = UUID.randomUUID();
        UUID fromDriverId = UUID.randomUUID();
        UUID toDriverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());
        order.setStatus(OrderStatus.RETURNING);
        order.setAssignedDriverId(fromDriverId);

        DispatchPlan sourcePlan = DispatchPlan.builder()
                .id(sourcePlanId)
                .tenant(tenant)
                .driverId(fromDriverId)
                .planStatus(PlanStatus.COMPLETED)
                .build();
        sourcePlan.addOrder(order, 0);
        DeliveryTask returnTask = DeliveryTask.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenantId(tenantId)
                .driverId(fromDriverId)
                .dispatchPlanId(sourcePlanId)
                .taskType("RETURN")
                .status(DeliveryTaskStatus.OUT_FOR_DELIVERY)
                .build();

        when(deliveryTaskRepository.findByOrder_IdAndTaskType(order.getId(), "RETURN"))
                .thenReturn(Optional.of(returnTask));
        when(dispatchPlanRepository.findById(sourcePlanId)).thenReturn(Optional.of(sourcePlan));

        orchestrationService.synchronizePackageTransfer(order, fromDriverId, toDriverId, "RETURN");

        assertThat(sourcePlan.getPlanStatus()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(sourcePlan.getOrders()).isEmpty();
        assertThat(order.getAssignedDriverId()).isEqualTo(toDriverId);
        assertThat(order.getPlanId()).isNull();
        assertThat(returnTask.getDriverId()).isEqualTo(toDriverId);
        assertThat(returnTask.getDispatchPlanId()).isNull();
        verify(dispatchPlanRepository, never()).findAllByDriverIdAndPlanStatusIn(
                toDriverId, List.of(PlanStatus.DISPATCHED));
    }

    @Test
    void recoveryRuleEngine_shouldRouteFirstFailureToRedeliveryAndMaxAttemptsToReturn() {
        com.swiftroute.orderservice.service.exception.RecoveryRuleEngine engine =
                new com.swiftroute.orderservice.service.exception.RecoveryRuleEngine(
                        null, null, outboxEventRepository);

        assertThat(engine.determineRecoveryAction("RECIPIENT_UNAVAILABLE", 1))
                .isEqualTo("REDELIVERY");
        assertThat(engine.determineRecoveryAction("RECIPIENT_UNAVAILABLE", 3))
                .isEqualTo("RETURN_TO_ORIGIN");
        assertThat(engine.determineRecoveryAction("REFUSED_BY_RECIPIENT", 1))
                .isEqualTo("RETURN_TO_ORIGIN");
    }

    private Tenant buildTenant(UUID tenantId) {
        return Tenant.builder()
                .id(tenantId)
                .businessName("Test Shop")
                .status(TenantStatus.APPROVED)
                .pickupLat(10.762)
                .pickupLng(106.660)
                .address("123 Depot St")
                .optimizationWeights(new OptimizationWeights(0.5, 0.5))
                .dispatchConfig(TenantDispatchConfig.builder()
                        .autoDispatchEnabled(true)
                        .batchSizeThreshold(3)
                        .maxWaitTimeSeconds(300)
                        .maxOrdersPerDriver(5)
                        .build())
                .build();
    }

    private Order buildOrder(Tenant tenant, OrderPriority priority, Instant createdAt) {
        return Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .recipientName("Recipient")
                .recipientPhone("0901234567")
                .pickupLat(10.762)
                .pickupLng(106.660)
                .deliveryLat(10.775)
                .deliveryLng(106.701)
                .deliveryAddress("456 Customer St")
                .weightKg(2.0)
                .priority(priority)
                .status(OrderStatus.READY_FOR_DISPATCH)
                .timeWindowStart(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
                .timeWindowEnd(OffsetDateTime.now(ZoneOffset.UTC).plusHours(2))
                .createdAt(createdAt)
                .build();
    }
}
