package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.orderservice.dto.request.BatchDeliveryTaskActionRequest;
import com.swiftroute.orderservice.dto.response.DriverDeliveryTaskResponse;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.repository.AuditLogRepository;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;

@ExtendWith(MockitoExtension.class)
class DeliveryExecutionFastTrackTest {

    @Mock
    private DeliveryTaskRepository tasks;
    @Mock
    private DeliveryAttemptRepository attempts;
    @Mock
    private OrderRepository orders;
    @Mock
    private AuditLogRepository auditLogs;
    @Mock
    private ReturnRequestRepository returnRequests;
    @Mock
    private PackageCustodyService custody;
    @Mock
    private OutboxEventRepository outboxEvents;

    private DeliveryExecutionService service;

    private UUID tenantId;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        service = new DeliveryExecutionService(
                tasks, attempts, orders, auditLogs, returnRequests, custody, outboxEvents);
        tenantId = UUID.randomUUID();
        driverId = UUID.randomUUID();
    }

    @Test
    void batchAccept_transitionsAssignedTasksToAccepted() {
        DeliveryTask t1 = sampleTask(DeliveryTaskStatus.ASSIGNED);
        DeliveryTask t2 = sampleTask(DeliveryTaskStatus.ASSIGNED);
        when(tasks.findByDriverIdAndStatusIn(any(), any())).thenReturn(List.of(t1, t2));
        when(tasks.findById(t1.getId())).thenReturn(Optional.of(t1));
        when(tasks.findById(t2.getId())).thenReturn(Optional.of(t2));

        List<DriverDeliveryTaskResponse> result = service.batchAccept(driverId, "DRIVER", null);

        assertThat(result).hasSize(2);
        assertThat(t1.getStatus()).isEqualTo(DeliveryTaskStatus.ACCEPTED);
        assertThat(t2.getStatus()).isEqualTo(DeliveryTaskStatus.ACCEPTED);
        verify(auditLogs, times(2)).save(any());
    }

    @Test
    void batchPickup_acquiresCustodyAndStartsDeliveryInOneSwipe() {
        DeliveryTask t1 = sampleTask(DeliveryTaskStatus.ACCEPTED);
        DeliveryTask t2 = sampleTask(DeliveryTaskStatus.ASSIGNED);
        when(tasks.findByDriverIdAndStatusIn(any(), any())).thenReturn(List.of(t1, t2));
        when(tasks.findById(t1.getId())).thenReturn(Optional.of(t1));
        when(tasks.findById(t2.getId())).thenReturn(Optional.of(t2));

        BatchDeliveryTaskActionRequest req = new BatchDeliveryTaskActionRequest(
                null,
                List.of(t1.getOrder().getId(), t2.getOrder().getId()),
                "Đã gom hàng tại kho");

        List<DriverDeliveryTaskResponse> result = service.batchPickup(driverId, "DRIVER", req);

        assertThat(result).hasSize(2);
        assertThat(t1.getStatus()).isEqualTo(DeliveryTaskStatus.OUT_FOR_DELIVERY);
        assertThat(t2.getStatus()).isEqualTo(DeliveryTaskStatus.OUT_FOR_DELIVERY);
        assertThat(t1.getOrder().getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
        assertThat(t2.getOrder().getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
        verify(custody).acquireAtPickup(t1.getOrder(), driverId);
        verify(custody).acquireAtPickup(t2.getOrder(), driverId);
    }

    private DeliveryTask sampleTask(DeliveryTaskStatus status) {
        Tenant tenant = Tenant.builder().id(tenantId).businessName("Shop Cần Thơ").build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .trackingToken(UUID.randomUUID().toString())
                .recipientName("Nguyễn Văn A")
                .recipientPhone("0901234567")
                .pickupLat(10.033)
                .pickupLng(105.787)
                .deliveryLat(10.045)
                .deliveryLng(105.770)
                .weightKg(2.0)
                .status(OrderStatus.PLANNED)
                .assignedDriverId(driverId)
                .build();
        return DeliveryTask.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenantId(tenantId)
                .driverId(driverId)
                .taskType("DELIVERY")
                .status(status)
                .version(0L)
                .build();
    }
}

