package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.dto.request.ApproveReturnRequest;
import com.swiftroute.orderservice.dto.request.CreateIncidentRequest;
import com.swiftroute.orderservice.dto.request.ReportReturnDiscrepancyRequest;
import com.swiftroute.orderservice.dto.request.ResolveIncidentRequest;
import com.swiftroute.orderservice.dto.response.DeliveryIncidentResponse;
import com.swiftroute.orderservice.dto.response.PackageUnitResponse;
import com.swiftroute.orderservice.dto.response.ReturnRequestResponse;
import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.DeliveryIncident;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.PickupLocation;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.DeliveryIncidentRepository;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.PickupLocationRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;
import com.swiftroute.orderservice.service.exception.RecoveryRuleEngine;

@ExtendWith(MockitoExtension.class)
class ExceptionOperationsServiceTest {

    @Mock
    private OrderRepository orders;
    @Mock
    private DeliveryIncidentRepository incidents;
    @Mock
    private ReturnRequestRepository returns;
    @Mock
    private DeliveryExecutionService execution;
    @Mock
    private PackageCustodyService custody;
    @Mock
    private RecoveryRuleEngine recoveryRuleEngine;
    @Mock
    private DispatchPlanRepository dispatchPlans;
    @Mock
    private PickupLocationRepository pickupLocations;
    @Mock
    private DeliveryAttemptRepository deliveryAttempts;
    @Mock
    private OutboxEventRepository outboxEvents;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExceptionOperationsService service;

    private UUID tenantId;
    private UUID driverId;
    private UUID operatorId;
    private UUID orderId;
    private Tenant tenant;
    private Order order;
    private PickupLocation defaultDepot;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        tenant = Tenant.builder()
                .id(tenantId)
                .businessName("SwiftRoute Logistics")
                .build();

        defaultDepot = PickupLocation.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Kho Trung Tam Q1")
                .build();

        order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .externalOrderCode("ORD-1001")
                .recipientName("Nguyen Van A")
                .recipientPhone("0901234567")
                .deliveryAddress("123 Le Loi, Q1")
                .pickupLocation(defaultDepot)
                .assignedDriverId(driverId)
                .packageCount(2)
                .status(OrderStatus.IN_TRANSIT)
                .codAmount(BigDecimal.ZERO)
                .build();
    }

    @Test
    void reportIncident_OrderDeliveryFailure_RecordsAttemptAndMarksFailed() {
        CreateIncidentRequest req = new CreateIncidentRequest(
                orderId,
                null,
                "CUSTOMER_UNREACHABLE",
                "MEDIUM",
                "Gọi 3 cuộc khách không bắt máy",
                List.of("https://res.cloudinary.com/demo/evidence1.jpg"),
                false,
                false,
                3);

        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(incidents.save(any(DeliveryIncident.class))).thenAnswer(inv -> {
            DeliveryIncident inc = inv.getArgument(0);
            inc.setId(UUID.randomUUID());
            inc.setCreatedAt(Instant.now());
            return inc;
        });

        DeliveryIncidentResponse response = service.reportIncident(req, tenantId, driverId, "DRIVER");

        assertThat(response.incidentType()).isEqualTo("CUSTOMER_UNREACHABLE");
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(deliveryAttempts).save(any(DeliveryAttempt.class));
        verify(outboxEvents).save(any(OutboxEvent.class));
    }

    @Test
    void reportIncident_OfflineSyncConflict_IsAuditOnlyAndPreservesTerminalOrder() {
        order.setStatus(OrderStatus.CANCELLED);
        CreateIncidentRequest req = new CreateIncidentRequest(
                orderId,
                null,
                "OFFLINE_SYNC_CONFLICT",
                "HIGH",
                "Tài xế đã ghi nhận giao thành công khi ngoại tuyến",
                List.of(),
                false,
                true,
                1);

        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(incidents.save(any(DeliveryIncident.class))).thenAnswer(inv -> {
            DeliveryIncident inc = inv.getArgument(0);
            inc.setId(UUID.randomUUID());
            inc.setCreatedAt(Instant.now());
            return inc;
        });

        DeliveryIncidentResponse response = service.reportIncident(req, tenantId, driverId, "DRIVER");

        assertThat(response.incidentType()).isEqualTo("OFFLINE_SYNC_CONFLICT");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(execution, never()).markDeliveryTaskFailed(any(Order.class), any(UUID.class));
        verify(deliveryAttempts, never()).save(any(DeliveryAttempt.class));
        verify(incidents).save(any(DeliveryIncident.class));
    }

    @Test
    void reportIncident_RouteLevelEmergencyWithoutOrderId_SavesRouteIncident() {
        UUID planId = UUID.randomUUID();
        DispatchPlan plan = DispatchPlan.builder()
                .id(planId)
                .tenant(tenant)
                .driverId(driverId)
                .planStatus(PlanStatus.DISPATCHED)
                .build();

        CreateIncidentRequest req = new CreateIncidentRequest(
                null,
                planId,
                "VEHICLE_BREAKDOWN",
                "CRITICAL",
                "Thủng lốp xe trên đường Nguyễn Hữu Cảnh",
                List.of(),
                false,
                true,
                0);

        when(dispatchPlans.findById(planId)).thenReturn(Optional.of(plan));
        when(incidents.save(any(DeliveryIncident.class))).thenAnswer(inv -> {
            DeliveryIncident inc = inv.getArgument(0);
            inc.setId(UUID.randomUUID());
            inc.setCreatedAt(Instant.now());
            return inc;
        });

        DeliveryIncidentResponse response = service.reportIncident(req, tenantId, driverId, "DRIVER");

        assertThat(response.orderId()).isNull();
        assertThat(response.dispatchPlanId()).isEqualTo(planId);
        assertThat(response.severity()).isEqualTo("CRITICAL");
        assertThat(response.needsImmediateAssistance()).isTrue();
    }

    @Test
    void resolveIncident_ReassignDriverWhilePackagesInCustody_ThrowsInvalidOrderStatus() {
        UUID incidentId = UUID.randomUUID();
        DeliveryIncident incident = DeliveryIncident.builder()
                .id(incidentId)
                .tenantId(tenantId)
                .order(order)
                .driverId(driverId)
                .incidentType("VEHICLE_BREAKDOWN")
                .status("OPEN")
                .build();

        PackageUnitResponse custodyPkg = new PackageUnitResponse(
                UUID.randomUUID(),
                orderId,
                "ORD-1001",
                1,
                "PKG-1",
                "IN_CUSTODY",
                driverId,
                0L,
                Instant.now());

        when(incidents.findByIdAndTenantId(incidentId, tenantId)).thenReturn(Optional.of(incident));
        when(custody.packagesForOrder(orderId, tenantId, operatorId, "OPERATOR"))
                .thenReturn(List.of(custodyPkg));

        ResolveIncidentRequest resolveReq = new ResolveIncidentRequest(
                "REASSIGN_DRIVER",
                "Đổi sang tài xế B",
                null,
                null,
                UUID.randomUUID());

        assertThatThrownBy(() -> service.resolveIncident(incidentId, tenantId, operatorId, "OPERATOR", resolveReq))
                .isInstanceOf(AppException.class);
    }

    @Test
    void resolveIncident_RequestReturn_CreatesReturnRequestAndKeepsIncidentInProgress() {
        UUID incidentId = UUID.randomUUID();
        DeliveryIncident incident = DeliveryIncident.builder()
                .id(incidentId)
                .tenantId(tenantId)
                .order(order)
                .driverId(driverId)
                .incidentType("CUSTOMER_REFUSED")
                .status("OPEN")
                .build();

        when(incidents.findByIdAndTenantId(incidentId, tenantId)).thenReturn(Optional.of(incident));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(returns.existsByOrder_IdAndStatusIn(eq(orderId), any())).thenReturn(false);
        when(returns.save(any(ReturnRequest.class))).thenAnswer(inv -> {
            ReturnRequest rr = inv.getArgument(0);
            rr.setId(UUID.randomUUID());
            rr.setCreatedAt(Instant.now());
            return rr;
        });

        ResolveIncidentRequest resolveReq = new ResolveIncidentRequest(
                "REQUEST_RETURN",
                "Khách từ chối nhận hàng, tạo phiếu hoàn về kho Q1",
                null,
                null,
                null);

        DeliveryIncidentResponse res = service.resolveIncident(incidentId, tenantId, operatorId, "OPERATOR", resolveReq);

        assertThat(res.status()).isEqualTo("IN_PROGRESS");
        assertThat(res.resolutionType()).isEqualTo("REQUEST_RETURN");
        verify(returns).save(any(ReturnRequest.class));
        var events = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEvents, org.mockito.Mockito.atLeastOnce()).save(events.capture());
        var resolution = events.getAllValues().stream()
                .filter(event -> event.getEventType().equals("incident.resolved")).findFirst().orElseThrow();
        var payload = (java.util.Map<?, ?>) resolution.getPayload();
        assertThat(payload.get("tenant_id")).isEqualTo(tenantId);
        assertThat(payload.get("status")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void returnToSenderLifecycle_ApproveToWarehouseReceipt_ReleasesCustodyAndMarksReturned() {
        UUID returnId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        DeliveryIncident linkedIncident = DeliveryIncident.builder()
                .id(incidentId)
                .tenantId(tenantId)
                .order(order)
                .incidentType("CUSTOMER_REFUSED")
                .status("IN_PROGRESS")
                .build();

        DeliveryTask returnTask = DeliveryTask.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .order(order)
                .driverId(driverId)
                .status(DeliveryTaskStatus.ASSIGNED)
                .build();

        ReturnRequest rr = ReturnRequest.builder()
                .id(returnId)
                .tenantId(tenantId)
                .order(order)
                .incident(linkedIncident)
                .returnLocation(defaultDepot)
                .currentHolderId(driverId)
                .reason("CUSTOMER_REFUSED")
                .status("REQUESTED")
                .packageCount(2)
                .build();

        when(returns.findByIdAndTenantId(returnId, tenantId)).thenReturn(Optional.of(rr));
        when(execution.createReturnTask(order, driverId)).thenReturn(returnTask);

        // 1. Operator approves return
        ReturnRequestResponse approved = service.approveReturn(
                returnId, tenantId, operatorId, "OPERATOR",
                new ApproveReturnRequest(null, driverId, "Approve RTS"));
        assertThat(approved.status()).isEqualTo("IN_EXECUTION");

        // 2. Driver arrives at return location
        ReturnRequestResponse arrived = service.arriveReturnLocation(returnId, tenantId, driverId, "DRIVER");
        assertThat(arrived.status()).isEqualTo("ARRIVED");

        // 3. Driver initiates handover
        ReturnRequestResponse awaiting = service.initiateReturnHandover(returnId, tenantId, driverId, "DRIVER");
        assertThat(awaiting.status()).isEqualTo("AWAITING_RECEIPT");
        // Order must NOT be RETURNED yet before warehouse staff confirms receipt
        assertThat(order.getStatus()).isNotEqualTo(OrderStatus.RETURNED);

        // 4. Warehouse staff confirms physical receipt
        UUID warehouseStaffId = UUID.randomUUID();
        ReturnRequestResponse completed = service.confirmReturnReceipt(
                returnId, tenantId, warehouseStaffId, "WAREHOUSE_STAFF", "Đã kiểm đủ 2 kiện nguyên vẹn");
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(linkedIncident.getStatus()).isEqualTo("RESOLVED");
        verify(custody).receiveReturn(order, warehouseStaffId, "Đã kiểm đủ 2 kiện nguyên vẹn");
        var events = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEvents, org.mockito.Mockito.atLeastOnce()).save(events.capture());
        var resolution = events.getAllValues().stream()
                .filter(event -> event.getEventType().equals("incident.resolved")).findFirst().orElseThrow();
        var payload = (java.util.Map<?, ?>) resolution.getPayload();
        assertThat(payload.get("tenant_id")).isEqualTo(tenantId);
        assertThat(payload.get("incident_id")).isEqualTo(incidentId);
        assertThat(payload.get("status")).isEqualTo("RESOLVED");
    }

    @Test
    void reportReturnDiscrepancy_MissingPackage_TransitionsToDiscrepancyAndOpensInvestigation() {
        UUID returnId = UUID.randomUUID();
        ReturnRequest rr = ReturnRequest.builder()
                .id(returnId)
                .tenantId(tenantId)
                .order(order)
                .returnLocation(defaultDepot)
                .currentHolderId(driverId)
                .reason("CUSTOMER_REFUSED")
                .status("AWAITING_RECEIPT")
                .packageCount(2)
                .build();

        when(returns.findByIdAndTenantId(returnId, tenantId)).thenReturn(Optional.of(rr));

        ReportReturnDiscrepancyRequest discReq = new ReportReturnDiscrepancyRequest(
                "MISSING_PACKAGE",
                1,
                "Chỉ bàn giao 1/2 kiện hàng",
                "https://res.cloudinary.com/demo/discrepancy.jpg");

        ReturnRequestResponse res = service.reportReturnDiscrepancy(
                returnId, tenantId, operatorId, "WAREHOUSE_STAFF", discReq);

        assertThat(res.status()).isEqualTo("DISCREPANCY");
        assertThat(res.discrepancyType()).isEqualTo("MISSING_PACKAGE");
        assertThat(res.actualQuantity()).isEqualTo(1);
        verify(incidents).save(any(DeliveryIncident.class));
    }
}
