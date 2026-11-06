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
