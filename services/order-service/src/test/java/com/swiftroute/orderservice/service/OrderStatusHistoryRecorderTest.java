package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OrderStatusHistory;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.repository.OrderStatusHistoryRepository;

@ExtendWith(MockitoExtension.class)
class OrderStatusHistoryRecorderTest {

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @InjectMocks
    private OrderStatusHistoryRecorder recorder;

    @Test
    void recordsTransitionWithNormalizedRoleAndTruncatedReason() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(Tenant.builder().id(tenantId).build())
                .status(OrderStatus.IN_TRANSIT)
                .build();
        String longReason = "r".repeat(600);

        recorder.record(order, OrderStatus.PLANNED, OrderStatus.IN_TRANSIT, actorId, " driver ", " " + longReason + " ");

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        OrderStatusHistory saved = captor.getValue();
        assertThat(saved.getOrder()).isSameAs(order);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getFromStatus()).isEqualTo("PLANNED");
        assertThat(saved.getToStatus()).isEqualTo("IN_TRANSIT");
        assertThat(saved.getChangedByUserId()).isEqualTo(actorId);
        assertThat(saved.getChangedByRole()).isEqualTo("DRIVER");
        assertThat(saved.getReason()).hasSize(500);
    }

    @Test
    void ignoresNoOpTransition() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(Tenant.builder().id(UUID.randomUUID()).build())
                .status(OrderStatus.PLANNED)
                .build();

        recorder.record(order, OrderStatus.PLANNED, OrderStatus.PLANNED, UUID.randomUUID(), "OPERATOR", "No-op");

        verify(historyRepository, never()).save(any());
    }

    @Test
    void recordsInitialOrderCreationWithNullFromStatus() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(Tenant.builder().id(UUID.randomUUID()).build())
                .status(OrderStatus.READY_FOR_DISPATCH)
                .build();

        recorder.record(order, null, OrderStatus.READY_FOR_DISPATCH, UUID.randomUUID(), "owner", "Tạo vận đơn mới");

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isNull();
        assertThat(captor.getValue().getToStatus()).isEqualTo("READY_FOR_DISPATCH");
        assertThat(captor.getValue().getChangedByRole()).isEqualTo("OWNER");
    }
}
