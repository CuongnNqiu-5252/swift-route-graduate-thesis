package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.swiftroute.orderservice.dto.request.CancelOrderRequest;
import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.request.ReportIncidentRequest;
import com.swiftroute.orderservice.dto.response.OperationsQueueResponse;
import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PackageCategory;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.PickupLocationRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceSecurityTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PackageCustodyService custody;
    @Mock private TenantRepository tenantRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private PickupLocationRepository pickupLocationRepository;
    @Mock private DeliveryZoneService deliveryZoneService;
    @Mock private OperationalReadinessService operationalReadiness;

    @InjectMocks
    private OrderService orderService;

    private UUID tenantId;
    private UUID driverId;
    private UUID orderId;
    private Tenant tenant;
    private Order order;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        tenant = Tenant.builder().id(tenantId).businessName("Test Tenant").build();
        order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .externalOrderCode("ORD-12345")
                .senderName("Sender")
                .senderPhone("0901234567")
                .pickupAddress("123 Sender St")
                .recipientName("Receiver")
                .recipientPhone("0907654321")
                .deliveryAddress("456 Receiver Ave")
                .deliveryLat(10.7769)
                .deliveryLng(106.7009)
                .status(OrderStatus.READY_FOR_DISPATCH)
                .packageDescription("Electronics")
                .weightKg(1.5)
                .codAmount(BigDecimal.ZERO)
                .timeWindowStart(OffsetDateTime.now().plusHours(1))
                .timeWindowEnd(OffsetDateTime.now().plusHours(2))
                .trackingToken("TOKEN-123")
                .build();
    }

    private CreateOrderRequest createSampleRequest() {
        return new CreateOrderRequest(
                "EXT-001",
                "Sender Name", "0901234567",
                "Receiver Name", "0907654321",
                null, 10.77, 106.70, "123 Pickup St",
                10.7769, 106.7009, "456 Delivery Ave",
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusHours(2),
                1.5, PackageCategory.PARCEL, "Electronics", "Handle with care",
                OrderPriority.NORMAL, BigDecimal.ZERO);
    }

    // -------------------------------------------------------------------------
    // 1. createOrder Role Boundaries (Feature 12)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createOrder: caller with DRIVER role is rejected with ACCESS_DENIED")
    void createOrder_whenCallerIsDriver_throwsAccessDenied() {
        CreateOrderRequest request = createSampleRequest();

        assertThatThrownBy(() -> orderService.createOrder(request, driverId, tenantId, "DRIVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        verify(tenantRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: caller with driver lowercase role is rejected with ACCESS_DENIED")
    void createOrder_whenCallerIsDriverLowercase_throwsAccessDenied() {
        CreateOrderRequest request = createSampleRequest();

        assertThatThrownBy(() -> orderService.createOrder(request, driverId, tenantId, "driver"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    // -------------------------------------------------------------------------
    // 2. cancelOrder Role Boundaries (Feature 12)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cancelOrder: caller with DRIVER role is rejected with ACCESS_DENIED")
    void cancelOrder_whenCallerIsDriver_throwsAccessDenied() {
        CancelOrderRequest request = new CancelOrderRequest("Customer canceled");

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, request, driverId, "DRIVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("cancelOrder: caller with OPERATOR role proceeds and cancels order")
    void cancelOrder_whenCallerIsOperator_succeeds() {
        CancelOrderRequest request = new CancelOrderRequest("Customer request");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(custody.holdsOrder(order)).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId, request, UUID.randomUUID(), "OPERATOR");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    // -------------------------------------------------------------------------
    // 3. getOrderById Driver Scoping (Feature 13)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getOrderById: assigned driver can view their assigned order")
    void getOrderById_whenCallerIsAssignedDriver_succeeds() {
        order.setAssignedDriverId(driverId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId, driverId, "DRIVER", tenantId);

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("getOrderById: driver cannot view order assigned to another driver (ORDER_ACCESS_DENIED)")
    void getOrderById_whenCallerIsOtherDriver_throwsOrderAccessDenied() {
        UUID otherDriverId = UUID.randomUUID();
        order.setAssignedDriverId(otherDriverId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(orderId, driverId, "DRIVER", tenantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
    }

    @Test
    @DisplayName("getOrderById: driver cannot view unassigned order even within same tenant (ORDER_ACCESS_DENIED)")
    void getOrderById_whenOrderIsUnassignedAndCallerIsDriver_throwsOrderAccessDenied() {
        order.setAssignedDriverId(null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(orderId, driverId, "DRIVER", tenantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
    }

    @Test
    @DisplayName("getOrderById: dispatcher (OPERATOR) can view unassigned order in tenant")
    void getOrderById_whenCallerIsDispatcher_succeedsAcrossAllTenantOrders() {
        order.setAssignedDriverId(null);
        Instant collectedAt = Instant.parse("2026-11-16T02:00:00Z");
        order.setCodStatus("IN_CUSTODY");
        order.setCodCollectedAt(collectedAt);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId, UUID.randomUUID(), "OPERATOR", tenantId);

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.codStatus()).isEqualTo("IN_CUSTODY");
        assertThat(response.codCollectedAt()).isEqualTo(collectedAt);
    }

    // -------------------------------------------------------------------------
    // 4. reportIncident Authorization Boundaries (Feature 14)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reportIncident: assigned driver can report incident")
    void reportIncident_whenCallerIsAssignedDriver_succeeds() {
        order.setAssignedDriverId(driverId);
        order.setStatus(OrderStatus.IN_TRANSIT);
        ReportIncidentRequest request = new ReportIncidentRequest("ACCIDENT", "Minor collision");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.reportIncident(orderId, request, driverId, "DRIVER");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("reportIncident: unassigned driver is rejected with ACCESS_DENIED")
    void reportIncident_whenCallerIsUnassignedDriver_throwsAccessDenied() {
        UUID otherDriverId = UUID.randomUUID();
        order.setAssignedDriverId(otherDriverId);
        order.setStatus(OrderStatus.IN_TRANSIT);
        ReportIncidentRequest request = new ReportIncidentRequest("CUSTOMER_UNREACHABLE", "Phone off");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.reportIncident(orderId, request, driverId, "DRIVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("reportIncident: VIEWER role is rejected with ACCESS_DENIED")
    void reportIncident_whenCallerIsViewer_throwsAccessDenied() {
        order.setStatus(OrderStatus.IN_TRANSIT);
        ReportIncidentRequest request = new ReportIncidentRequest("PACKAGE_DAMAGED", "Torn box");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.reportIncident(orderId, request, UUID.randomUUID(), "VIEWER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("reportIncident: OPERATOR dispatcher can report incident")
    void reportIncident_whenCallerIsOperator_succeeds() {
        order.setStatus(OrderStatus.IN_TRANSIT);
        ReportIncidentRequest request = new ReportIncidentRequest("WEATHER_DELAY", "Heavy rain flood");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.reportIncident(orderId, request, UUID.randomUUID(), "OPERATOR");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository).save(order);
    }

    // -------------------------------------------------------------------------
    // 5. getOperationsQueue Status Grouping
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getOperationsQueue: excludes CANCELLED orders and counts only FAILED in intervention")
    void getOperationsQueue_excludesCancelledFromIntervention() {
        Order paidOrder = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .externalOrderCode("ORD-PAID")
                .recipientName("A")
                .recipientPhone("0900000001")
                .pickupAddress("Pickup")
                .deliveryAddress("Delivery")
                .status(OrderStatus.READY_FOR_DISPATCH)
                .weightKg(1.0)
                .codAmount(BigDecimal.ZERO)
                .build();
        Order inTransitOrder = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .externalOrderCode("ORD-TRANSIT")
                .recipientName("B")
                .recipientPhone("0900000002")
                .pickupAddress("Pickup")
                .deliveryAddress("Delivery")
                .status(OrderStatus.IN_TRANSIT)
                .weightKg(1.0)
                .codAmount(BigDecimal.ZERO)
                .build();
        Order failedOrder = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .externalOrderCode("ORD-FAILED")
                .recipientName("C")
                .recipientPhone("0900000003")
                .pickupAddress("Pickup")
                .deliveryAddress("Delivery")
                .status(OrderStatus.FAILED)
                .weightKg(1.0)
                .codAmount(BigDecimal.ZERO)
                .build();
        Order cancelledOrder = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .externalOrderCode("ORD-CANCELLED")
                .recipientName("D")
                .recipientPhone("0900000004")
                .pickupAddress("Pickup")
                .deliveryAddress("Delivery")
                .status(OrderStatus.CANCELLED)
                .weightKg(1.0)
                .codAmount(BigDecimal.ZERO)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(orderRepository.findAllByTenantIdAndStatusInOrderByUpdatedAtDesc(
                tenantId,
                List.of(OrderStatus.READY_FOR_DISPATCH, OrderStatus.PLANNED, OrderStatus.IN_TRANSIT,
                        OrderStatus.FAILED, OrderStatus.RETURNING)))
                .thenReturn(List.of(paidOrder, inTransitOrder, failedOrder, cancelledOrder));

        OperationsQueueResponse queue = orderService.getOperationsQueue(tenantId, UUID.randomUUID(), "OPERATOR");

        assertThat(queue.readyForDispatch()).extracting(OrderResponse::orderId).containsExactly(paidOrder.getId());
        assertThat(queue.active()).extracting(OrderResponse::orderId).containsExactly(inTransitOrder.getId());
        assertThat(queue.intervention()).extracting(OrderResponse::orderId).containsExactly(failedOrder.getId());
    }
}
