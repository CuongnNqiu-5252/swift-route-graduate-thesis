package com.swiftroute.orderservice.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.client.RoutingEngineClient;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse.LocationPoint;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse.OrderedStop;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.DispatchPlanOrder;
import com.swiftroute.orderservice.entity.DispatchPlanOrder.DispatchPlanOrderId;
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
        Tenant tenant = buildTenant(tenantId, false);
        Order urgentOrder = buildOrder(tenant, OrderPriority.URGENT, Instant.now());

        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.PAID))
                .thenReturn(List.of(urgentOrder));

        Optional<PlanTriggerType> trigger = eligibilityEvaluator.evaluate(tenant, Instant.now());
        assertThat(trigger).contains(PlanTriggerType.URGENT);
    }

    @Test
    void evaluate_shouldTriggerOnMaxWaitTime() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId, false);
        Order oldOrder = buildOrder(tenant, OrderPriority.NORMAL, Instant.now().minusSeconds(400));

        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.PAID))
                .thenReturn(List.of(oldOrder));

        Optional<PlanTriggerType> trigger = eligibilityEvaluator.evaluate(tenant, Instant.now());
        assertThat(trigger).contains(PlanTriggerType.MAX_WAIT);
    }

    @Test
    void executePlanningCycle_shouldCreateAutoApproveAndDispatchPlan() {
        UUID tenantId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId, false);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now());

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.PAID))
                .thenReturn(List.of(order));
        when(operationalReadinessService.fleetState(tenantId))
                .thenReturn(new OperationalReadinessService.FleetState(
                        true, List.of(Map.of("user_id", driverId.toString()))));

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

        DeliveryTask dummyTask = DeliveryTask.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenantId(tenantId)
                .driverId(driverId)
                .status(DeliveryTaskStatus.ASSIGNED)
                .build();
        when(deliveryExecutionService.assign(order, driverId)).thenReturn(dummyTask);

        Optional<DispatchPlan> result = orchestrationService.executePlanningCycle(
                tenantId, PlanTriggerType.BATCH_SIZE);

        assertThat(result).isPresent();
        DispatchPlan plan = result.get();
        assertThat(plan.getPlanStatus()).isEqualTo(PlanStatus.DISPATCHED);
        assertThat(plan.getDriverId()).isEqualTo(driverId);
        assertThat(plan.getAlgorithmUsed()).isEqualTo("ortools");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(order.getAssignedDriverId()).isEqualTo(driverId);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void executePlanningCycle_shouldUseOrderPickupLocationAsDepotAndGroupSameWarehouse() {
        UUID tenantId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId, false);

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
        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.PAID))
                .thenReturn(List.of(warehouseBOrder, warehouseAOrder));
        when(operationalReadinessService.fleetState(tenantId))
                .thenReturn(new OperationalReadinessService.FleetState(
                        true, List.of(Map.of("user_id", driverId.toString()))));

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

        DeliveryTask dummyTask = DeliveryTask.builder()
                .id(UUID.randomUUID())
                .order(warehouseBOrder)
                .tenantId(tenantId)
                .driverId(driverId)
                .status(DeliveryTaskStatus.ASSIGNED)
                .build();
        when(deliveryExecutionService.assign(warehouseBOrder, driverId)).thenReturn(dummyTask);

        Optional<DispatchPlan> result = orchestrationService.executePlanningCycle(
                tenantId, PlanTriggerType.URGENT);

        assertThat(result).isPresent();
        com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.depot().lat()).isEqualTo(10.8505);
        assertThat(sentRequest.depot().lng()).isEqualTo(106.7718);
        assertThat(sentRequest.depot().address()).isEqualTo("789 Vo Van Ngan, Thu Duc");
        assertThat(sentRequest.stops()).hasSize(1);
        assertThat(sentRequest.stops().get(0).id()).isEqualTo(warehouseBOrder.getId().toString());
        assertThat(warehouseAOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void executePlanningCycle_manualTriggerShouldPlanAllWarehouseGroupsWithDistinctDrivers() {
        UUID tenantId = UUID.randomUUID();
        UUID driver1Id = UUID.randomUUID();
        UUID driver2Id = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId, false);

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
        when(orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.PAID))
                .thenReturn(List.of(warehouseAOrder1, warehouseAOrder2, warehouseBOrder));
        when(operationalReadinessService.fleetState(tenantId))
                .thenReturn(new OperationalReadinessService.FleetState(
                        true, List.of(
                                Map.of("user_id", driver1Id.toString()),
                                Map.of("user_id", driver2Id.toString()))));

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

        when(deliveryExecutionService.assign(any(Order.class), any(UUID.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            UUID dId = inv.getArgument(1);
            return DeliveryTask.builder()
                    .id(UUID.randomUUID())
                    .order(o)
                    .tenantId(tenantId)
                    .driverId(dId)
                    .status(DeliveryTaskStatus.ASSIGNED)
                    .build();
        });

        Optional<DispatchPlan> result = orchestrationService.executePlanningCycle(
                tenantId, PlanTriggerType.MANUAL);

        assertThat(result).isPresent();
        assertThat(warehouseAOrder1.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(warehouseAOrder2.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(warehouseBOrder.getStatus()).isEqualTo(OrderStatus.PLANNED);
        assertThat(warehouseAOrder1.getAssignedDriverId()).isEqualTo(driver1Id);
        assertThat(warehouseAOrder2.getAssignedDriverId()).isEqualTo(driver1Id);
        assertThat(warehouseBOrder.getAssignedDriverId()).isEqualTo(driver2Id);
        assertThat(warehouseAOrder1.getPlanId()).isNotEqualTo(warehouseBOrder.getPlanId());
    }

    @Test
    void acknowledgementTimeout_shouldNotReassignToDriverWithAnotherActivePlan() {
        UUID tenantId = UUID.randomUUID();
        UUID timedOutPlanId = UUID.randomUUID();
        UUID otherPlanId = UUID.randomUUID();
        UUID currentDriverId = UUID.randomUUID();
        UUID busyDriverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId, false);

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
        when(dispatchPlanRepository.save(any(DispatchPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchPlan result = orchestrationService.handleAcknowledgementTimeout(timedOutPlanId);

        assertThat(result.getPlanStatus()).isEqualTo(PlanStatus.CANCELLED);
        assertThat(result.getDriverId()).isEqualTo(currentDriverId);
    }

    @Test
    void acknowledgementTimeout_shouldUseAFreeDriverWhenBusyDriversAreExcluded() {
        UUID tenantId = UUID.randomUUID();
        UUID timedOutPlanId = UUID.randomUUID();
        UUID currentDriverId = UUID.randomUUID();
        UUID busyDriverId = UUID.randomUUID();
        UUID freeDriverId = UUID.randomUUID();
        Tenant tenant = buildTenant(tenantId, false);

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
        Tenant tenant = buildTenant(tenantId, false);
        Order order = buildOrder(tenant, OrderPriority.NORMAL, Instant.now().minusSeconds(600));
        order.setStatus(OrderStatus.IN_TRANSIT);
        order.setFulfillmentStatus("IN_TRANSIT");
        order.setAssignedDriverId(driverId);
        order.setPlanId(planId);

        DispatchPlan plan = DispatchPlan.builder()
                .id(planId)
                .tenant(tenant)
                .driverId(driverId)
                .planStatus(PlanStatus.DISPATCHED)
                .ackDeadlineAt(Instant.now().minusSeconds(60))
                .plannedAt(Instant.now().minusSeconds(600))
                .build();
        plan.getPlanOrders().add(DispatchPlanOrder.builder()
                .id(new DispatchPlanOrderId(planId, order.getId()))
                .plan(plan)
                .order(order)
                .stopIndex(0)
                .build());
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
        assertThat(order.getFulfillmentStatus()).isEqualTo("IN_TRANSIT");
        assertThat(order.getAssignedDriverId()).isEqualTo(driverId);
        assertThat(order.getPlanId()).isEqualTo(planId);
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

    private Tenant buildTenant(UUID tenantId, boolean requireApproval) {
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
                        .requireApproval(requireApproval)
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
                .status(OrderStatus.PAID)
                .fulfillmentStatus("READY_FOR_PLANNING")
                .timeWindowStart(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
                .timeWindowEnd(OffsetDateTime.now(ZoneOffset.UTC).plusHours(2))
                .createdAt(createdAt)
                .build();
    }
}
