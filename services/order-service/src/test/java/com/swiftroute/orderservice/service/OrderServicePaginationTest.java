package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.PickupLocationRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class OrderServicePaginationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PackageCustodyService custody;
    @Mock private TenantRepository tenantRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private PickupLocationRepository pickupLocationRepository;
    @Mock private DeliveryZoneService deliveryZoneService;
    @InjectMocks private OrderService orderService;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void defaultGetOrderPageSortsByCreatedAtDescending() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(orderRepository.countByTenantGroupedByStatus(tenantId)).thenReturn(List.of());

        orderService.getOrderPage(tenantId, null, null, null, null, 0, 25);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Pageable used = pageableCaptor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(0);
        assertThat(used.getPageSize()).isEqualTo(25);
        assertThat(used.getSort()).containsExactly(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getOrderPageAppliesRequestedDateSortFieldAndDirection() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(orderRepository.countByTenantGroupedByStatus(tenantId)).thenReturn(List.of());

        orderService.getOrderPage(tenantId, null, null, null, null, 2, 10, "time_window_start", "asc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Pageable used = pageableCaptor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(10);
        assertThat(used.getSort()).containsExactly(
                Sort.Order.asc("timeWindowStart"),
                Sort.Order.asc("id"));
    }

    @Test
    void getOrderPageRejectsUnsupportedSortFieldOrDirection() {
        assertThatThrownBy(() -> orderService.getOrderPage(
                tenantId, null, null, null, null, 0, 15, "sql_injection", "asc"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThatThrownBy(() -> orderService.getOrderPage(
                tenantId, null, null, null, null, 0, 15, "created_at", "sideways"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
