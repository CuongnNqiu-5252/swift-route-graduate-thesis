package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftroute.orderservice.dto.request.BatchDeliveryTaskActionRequest;
import com.swiftroute.orderservice.dto.request.UpdateDeliveryTaskRequest;
import com.swiftroute.orderservice.dto.response.DriverDeliveryTaskResponse;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.AuditLogRepository;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
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
    @Mock
    private DispatchPlanRepository dispatchPlans;
    @Mock
    private RecipientDeliveryEvents recipientDeliveryEvents;

    private DeliveryExecutionService service;

    private UUID tenantId;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        service = new DeliveryExecutionService(
                tasks, attempts, orders, auditLogs, returnRequests, custody, outboxEvents, dispatchPlans, recipientDeliveryEvents);
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
        ReflectionTestUtils.setField(service, "deliveryStartedEventEnabled", true);
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
        ArgumentCaptor<OutboxEvent> events = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEvents, times(2)).save(events.capture());
        assertThat(events.getAllValues()).extracting(OutboxEvent::getAggregateId)
                .containsExactlyInAnyOrder(t1.getId(), t2.getId());
    }

    @Test
    void deliveryStartRecordsOneEventWhenTheDriverRepeatsTheRequest() {
        ReflectionTestUtils.setField(service, "deliveryStartedEventEnabled", true);
        DeliveryTask task = sampleTask(DeliveryTaskStatus.ACCEPTED);
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        var request = new UpdateDeliveryTaskRequest(DeliveryTaskStatus.OUT_FOR_DELIVERY, null, null, null);

        service.update(task.getId(), driverId, "DRIVER", request);
        String startedAt = task.getUpdatedAt().toString();
        service.update(task.getId(), driverId, "DRIVER", request);

        ArgumentCaptor<OutboxEvent> events = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEvents).save(events.capture());
        OutboxEvent event = events.getValue();
        assertThat(event.getEventType()).isEqualTo("order.delivery_started.v1");
        assertThat(event.getAggregateId()).isEqualTo(task.getId());
        assertThat(event.getPayload()).isEqualTo(Map.of(
                "event_id", event.getEventId().toString(),
                "tenant_id", tenantId.toString(),
                "order_id", task.getOrder().getId().toString(),
                "delivery_task_id", task.getId().toString(),
                "started_at", startedAt));
    }

    @Test
    void deliveryStartDoesNotPublishBeforeTheConsumerIsEnabled() {
        DeliveryTask task = sampleTask(DeliveryTaskStatus.ACCEPTED);
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        var request = new UpdateDeliveryTaskRequest(DeliveryTaskStatus.OUT_FOR_DELIVERY, null, null, null);

        service.update(task.getId(), driverId, "DRIVER", request);

        assertThat(task.getOrder().getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
        verifyNoInteractions(outboxEvents);
    }

    @Test
    void returnPickupDoesNotPublishARecipientDeliveryStart() {
        ReflectionTestUtils.setField(service, "deliveryStartedEventEnabled", true);
        DeliveryTask task = sampleTask(DeliveryTaskStatus.ACCEPTED);
        task.setTaskType("RETURN");
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        var request = new UpdateDeliveryTaskRequest(DeliveryTaskStatus.OUT_FOR_DELIVERY, null, null, null);

        service.update(task.getId(), driverId, "DRIVER", request);

        assertThat(task.getOrder().getStatus()).isEqualTo(OrderStatus.RETURNING);
        verifyNoInteractions(outboxEvents);
    }

    @Test
    void updateRejectsAnOldTaskAfterItsOrderReturnsToTheDispatchQueue() {
        DeliveryTask task = sampleTask(DeliveryTaskStatus.ASSIGNED);
        task.setDispatchPlanId(UUID.randomUUID());
        task.getOrder().setAssignedDriverId(null);
        task.getOrder().setStatus(OrderStatus.READY_FOR_DISPATCH);
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        var request = new UpdateDeliveryTaskRequest(DeliveryTaskStatus.ACCEPTED, null, null, null);

        assertThatThrownBy(() -> service.update(task.getId(), driverId, "DRIVER", request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        assertThat(task.getOrder().getPlanId()).isNull();
        assertThat(task.getStatus()).isEqualTo(DeliveryTaskStatus.ASSIGNED);
    }

    @Test
    void orphanedAcceptedTaskAfterPlanCancelIsHiddenAndRejected() {
        DeliveryTask task = sampleTask(DeliveryTaskStatus.ACCEPTED);
        task.setDispatchPlanId(null);
        task.getOrder().setAssignedDriverId(null);
        task.getOrder().setStatus(OrderStatus.READY_FOR_DISPATCH);
        when(tasks.findByDriverIdAndStatusIn(any(), any())).thenReturn(List.of(task));
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));

        assertThat(service.activeForDriver(driverId, "DRIVER")).isEmpty();

        var pickupRequest = new UpdateDeliveryTaskRequest(DeliveryTaskStatus.OUT_FOR_DELIVERY, null, null, null);
        assertThatThrownBy(() -> service.update(task.getId(), driverId, "DRIVER", pickupRequest))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void assignReactivatesCancelledTaskForSameOrDifferentDriver() {
        DeliveryTask cancelledTask = sampleTask(DeliveryTaskStatus.CANCELLED);
        cancelledTask.getOrder().setAssignedDriverId(null);
        cancelledTask.getOrder().setStatus(OrderStatus.READY_FOR_DISPATCH);
        UUID newDriverId = UUID.randomUUID();
        when(tasks.findByOrder_IdAndTaskType(cancelledTask.getOrder().getId(), "DELIVERY"))
                .thenReturn(Optional.of(cancelledTask));

        DeliveryTask reassigned = service.assign(cancelledTask.getOrder(), newDriverId);

        assertThat(reassigned.getDriverId()).isEqualTo(newDriverId);
        assertThat(reassigned.getStatus()).isEqualTo(DeliveryTaskStatus.ASSIGNED);
    }

    @Test
    void update_completesDispatchPlan_whenAllTasksAreCompleted() {
        UUID planId = UUID.randomUUID();
        DeliveryTask task = sampleTask(DeliveryTaskStatus.OUT_FOR_DELIVERY);
        task.setDispatchPlanId(planId);

        Tenant tenant = task.getOrder().getTenant();
        DispatchPlan plan = DispatchPlan.builder()
                .id(planId)
                .tenant(tenant)
                .driverId(driverId)
                .planStatus(PlanStatus.DISPATCHED)
                .build();
        plan.addOrder(task.getOrder(), 0);

        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        when(dispatchPlans.findById(planId)).thenReturn(Optional.of(plan));
        when(tasks.findByDispatchPlanId(planId)).thenReturn(List.of(task));
        when(attempts.maxAttemptNumber(task.getOrder().getId())).thenReturn(0);

        com.swiftroute.orderservice.dto.request.UpdateDeliveryTaskRequest req =
                new com.swiftroute.orderservice.dto.request.UpdateDeliveryTaskRequest(
                        DeliveryTaskStatus.COMPLETED, null, "Giao thành công", null);

        service.update(task.getId(), driverId, "DRIVER", req);

        assertThat(task.getStatus()).isEqualTo(DeliveryTaskStatus.COMPLETED);
        assertThat(plan.getPlanStatus()).isEqualTo(PlanStatus.COMPLETED);
        verify(dispatchPlans).save(plan);
        verify(outboxEvents, atLeastOnce()).save(any());
    }

    @Test
    void createReturnTask_startsReturnWithoutDriverPickupConfirmation() {
        DeliveryTask task = sampleTask(DeliveryTaskStatus.ASSIGNED);
        when(tasks.save(any(DeliveryTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryTask result = service.createReturnTask(task.getOrder(), driverId);

        assertThat(result.getStatus()).isEqualTo(DeliveryTaskStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void completeDeliveryPlanIfFinished_closesRouteAfterItsLastDeliveryMovesToReturn() {
        DeliveryTask task = sampleTask(DeliveryTaskStatus.FAILED);
        UUID planId = UUID.randomUUID();
        DispatchPlan plan = DispatchPlan.builder()
                .id(planId)
                .tenant(task.getOrder().getTenant())
                .driverId(driverId)
                .planStatus(PlanStatus.DISPATCHED)
                .build();
        plan.addOrder(task.getOrder(), 0);
        DeliveryTask remaining = sampleTask(DeliveryTaskStatus.OUT_FOR_DELIVERY);
        when(dispatchPlans.findById(planId)).thenReturn(Optional.of(plan));
        when(tasks.findByDispatchPlanId(planId)).thenReturn(List.of(task, remaining));

        service.completeDeliveryPlanIfFinished(task.getOrder());
        assertThat(plan.getPlanStatus()).isEqualTo(PlanStatus.DISPATCHED);

        remaining.setStatus(DeliveryTaskStatus.COMPLETED);
        service.completeDeliveryPlanIfFinished(task.getOrder());
        assertThat(plan.getPlanStatus()).isEqualTo(PlanStatus.COMPLETED);
        verify(dispatchPlans).save(plan);
    }

    @Test
    void legacyAssignedReturnCanBeHandedOverDirectlyAndRetryDoesNotReopenReceipt() {
        DeliveryTask task = sampleTask(DeliveryTaskStatus.ASSIGNED);
        task.setTaskType("RETURN");
        Order order = task.getOrder();
        order.setStatus(OrderStatus.RETURNING);
        ReturnRequest request = ReturnRequest.builder().status("IN_EXECUTION").build();
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        when(returnRequests.findByReturnTask_Id(task.getId())).thenReturn(Optional.of(request));
        var handover = new UpdateDeliveryTaskRequest(DeliveryTaskStatus.COMPLETED, null, "Đã bàn giao", null);

        service.update(task.getId(), driverId, "DRIVER", handover);

        assertThat(request.getStatus()).isEqualTo("AWAITING_RECEIPT");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNING);
        verify(custody).requireReturnCustody(order, driverId);

        request.setStatus("COMPLETED");
        order.setStatus(OrderStatus.RETURNED);
        service.update(task.getId(), driverId, "DRIVER", handover);

        assertThat(request.getStatus()).isEqualTo("COMPLETED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
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

