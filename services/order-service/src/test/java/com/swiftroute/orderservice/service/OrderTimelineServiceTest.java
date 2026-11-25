package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.swiftroute.orderservice.dto.response.OrderTimelineResponse;
import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.DeliveryIncident;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OrderStatusHistory;
import com.swiftroute.orderservice.entity.PackageHandover;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.DeliveryIncidentRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OrderStatusHistoryRepository;
import com.swiftroute.orderservice.repository.PackageHandoverRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;

@ExtendWith(MockitoExtension.class)
class OrderTimelineServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock private DeliveryIncidentRepository deliveryIncidentRepository;
    @Mock private PackageHandoverRepository packageHandoverRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;

    @InjectMocks private OrderTimelineService service;

    private UUID tenantId;
    private UUID orderId;
    private UUID driverId;
    private UUID operatorId;
    private Order order;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
        Instant created = Instant.parse("2026-11-22T01:00:00Z");

        order = Order.builder()
                .id(orderId)
                .tenant(Tenant.builder().id(tenantId).ownerUserId(operatorId).build())
                .externalOrderCode("ORD-1001")
                .trackingToken("TRK123456789")
                .recipientName("Nguyen Van A")
                .recipientPhone("0901234567")
                .pickupLat(10.0)
                .pickupLng(106.0)
                .deliveryLat(10.1)
                .deliveryLng(106.1)
                .weightKg(1.5)
                .status(OrderStatus.DELIVERED)
                .assignedDriverId(driverId)
                .custodianDriverId(null)
                .codAmount(new BigDecimal("150000"))
                .codStatus("IN_CUSTODY")
                .codCollectedAt(Instant.parse("2026-11-22T05:00:00Z"))
                .createdAt(created)
                .updatedAt(Instant.parse("2026-11-22T05:10:00Z"))
                .build();
    }

    @Test
    void aggregatesAllSourcesNewestFirstAndExtractsLatestProofUrl() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(statusHistoryRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)).thenReturn(List.of(
                OrderStatusHistory.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .tenantId(tenantId)
                        .fromStatus("IN_TRANSIT")
                        .toStatus("DELIVERED")
                        .changedByUserId(driverId)
                        .changedByRole("DRIVER")
                        .reason("Khách đã ký nhận")
                        .createdAt(Instant.parse("2026-11-22T05:05:00Z"))
                        .build(),
                OrderStatusHistory.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .tenantId(tenantId)
                        .fromStatus(null)
                        .toStatus("READY_FOR_DISPATCH")
                        .changedByUserId(operatorId)
                        .changedByRole("OPERATOR")
                        .reason("Tạo vận đơn mới")
                        .createdAt(Instant.parse("2026-11-22T01:00:00Z"))
                        .build()));
        when(deliveryAttemptRepository.findAllByOrder_IdOrderByCompletedAtDesc(orderId)).thenReturn(List.of(
                DeliveryAttempt.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .tenantId(tenantId)
                        .attemptNumber(2)
                        .outcome("SUCCESS")
                        .proofUrl(" https://cdn.example.com/pod.jpg ")
                        .completedBy(driverId)
                        .completedAt(Instant.parse("2026-11-22T05:04:00Z"))
                        .build(),
                DeliveryAttempt.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .tenantId(tenantId)
                        .attemptNumber(1)
                        .outcome("FAILED")
                        .failureReason("CUSTOMER_UNREACHABLE")
                        .notes("Gọi 3 lần không nghe")
                        .completedBy(driverId)
                        .completedAt(Instant.parse("2026-11-22T02:00:00Z"))
                        .build()));
        when(deliveryIncidentRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)).thenReturn(List.of(
                DeliveryIncident.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .tenantId(tenantId)
                        .reportedBy(driverId)
                        .driverId(driverId)
                        .incidentType("CUSTOMER_UNREACHABLE")
                        .severity("MEDIUM")
                        .notes("Khách hẹn giao chiều")
                        .evidenceUrls("https://cdn.example.com/e1.jpg, https://cdn.example.com/e2.jpg")
                        .status("RESOLVED")
                        .resolutionType("RETRY_DELIVERY")
                        .resolvedBy(operatorId)
                        .resolutionNotes("Giao lại buổi chiều")
                        .createdAt(Instant.parse("2026-11-22T02:05:00Z"))
                        .resolvedAt(Instant.parse("2026-11-22T02:30:00Z"))
                        .build()));
        when(packageHandoverRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)).thenReturn(List.of(
                PackageHandover.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .tenantId(tenantId)
                        .fromDriverId(UUID.randomUUID())
                        .toDriverId(driverId)
                        .status("COMPLETED")
                        .reason("Xe hỏng dọc đường")
                        .receiverNotes("Đã nhận đủ hàng")
                        .createdAt(Instant.parse("2026-11-22T03:00:00Z"))
                        .completedAt(Instant.parse("2026-11-22T03:15:00Z"))
                        .build()));
        when(returnRequestRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)).thenReturn(List.of(
                ReturnRequest.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .tenantId(tenantId)
                        .reason("Kiểm thử hoàn kho")
                        .status("COMPLETED")
                        .requestedBy(operatorId)
                        .receivedBy(operatorId)
                        .completionNotes("Đã nhận")
                        .discrepancyEvidenceUrl("https://cdn.example.com/ret.jpg")
                        .createdAt(Instant.parse("2026-11-22T04:00:00Z"))
                        .receivedAt(Instant.parse("2026-11-22T04:15:00Z"))
                        .build()));

        OrderTimelineResponse result = service.getOrderTimeline(orderId, operatorId, "OPERATOR", tenantId);

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.orderCode()).isEqualTo("ORD-1001");
        assertThat(result.trackingToken()).isEqualTo("TRK123456789");
        assertThat(result.latestProofUrl()).isEqualTo("https://cdn.example.com/pod.jpg");
        assertThat(result.events()).hasSize(11);
        assertThat(result.events().get(0).eventType()).isEqualTo("ORDER_STATUS_CHANGED");
        assertThat(result.events().get(result.events().size() - 1).eventType()).isEqualTo("ORDER_CREATED");

        var incidentOpen = result.events().stream()
                .filter(e -> "INCIDENT_REPORTED".equals(e.eventType()))
                .findFirst()
                .orElseThrow();
        assertThat(incidentOpen.evidenceUrls())
                .containsExactly("https://cdn.example.com/e1.jpg", "https://cdn.example.com/e2.jpg");
    }

    @Test
    void synthesizesBaselineMilestonesWhenStatusHistoryIsEmpty() {
        order.setStatus(OrderStatus.IN_TRANSIT);
        order.setCodCollectedAt(null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(statusHistoryRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)).thenReturn(List.of());
        when(deliveryAttemptRepository.findAllByOrder_IdOrderByCompletedAtDesc(orderId)).thenReturn(List.of());
        when(deliveryIncidentRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)).thenReturn(List.of());
        when(packageHandoverRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)).thenReturn(List.of());
        when(returnRequestRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)).thenReturn(List.of());

        OrderTimelineResponse result = service.getOrderTimeline(orderId, operatorId, "OPERATOR", tenantId);

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).toStatus()).isEqualTo("IN_TRANSIT");
        assertThat(result.events().get(1).eventType()).isEqualTo("ORDER_CREATED");
    }

    @Test
    void rejectsDriverRequestingAnotherDriversOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        UUID otherDriver = UUID.randomUUID();

        assertThatThrownBy(() -> service.getOrderTimeline(orderId, otherDriver, "DRIVER", tenantId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
    }
}
