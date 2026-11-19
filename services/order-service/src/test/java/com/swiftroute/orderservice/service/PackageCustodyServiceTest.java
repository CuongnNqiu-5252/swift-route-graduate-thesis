package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.swiftroute.orderservice.dto.request.AssignPackageHandoverRequest;
import com.swiftroute.orderservice.dto.request.CreatePackageHandoverRequest;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.PackageCustodyEvent;
import com.swiftroute.orderservice.entity.PackageHandover;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.PackageCustodyEventRepository;
import com.swiftroute.orderservice.repository.PackageHandoverRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;
import com.swiftroute.orderservice.service.planning.DispatchOrchestrationService;

@ExtendWith(MockitoExtension.class)
class PackageCustodyServiceTest {
    @Mock private PackageCustodyEventRepository events;
    @Mock private PackageHandoverRepository handovers;
    @Mock private OrderRepository orders;
    @Mock private ReturnRequestRepository returns;
    @Mock private OperationalReadinessService readiness;
    @Mock private ObjectProvider<DispatchOrchestrationService> dispatchProvider;
    @Mock private DispatchOrchestrationService dispatch;
    @InjectMocks private PackageCustodyService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();
    private final UUID receiver = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID();
    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.builder().id(UUID.randomUUID()).tenant(Tenant.builder().id(tenantId).build())
                .assignedDriverId(driver).status(OrderStatus.IN_TRANSIT).build();
    }

    @Test
    void pickupRecordsCustodyAgainstOrder() {
        service.acquireAtPickup(order, driver);
        verify(events).save(org.mockito.ArgumentMatchers.argThat(event ->
                event.getOrder() == order && driver.equals(event.getToCustodianId())
                        && "PICKUP_CONFIRMED".equals(event.getEventType())));
    }

    @Test
    void repeatedPickupKeepsExistingCustodyHistory() {
        givenHeldOrder();
        service.acquireAtPickup(order, driver);
        verify(events, never()).save(any());
    }

    @Test
    void handoverRequestUsesTheOrderAndKeepsTheCurrentDriver() {
        givenHeldOrder();
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(handovers.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.createHandover(tenantId, driver, "DRIVER",
                new CreatePackageHandoverRequest(order.getId(), "Xe hỏng"));
        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.toDriverId()).isNull();
        assertThat(order.getAssignedDriverId()).isEqualTo(driver);
    }

    @Test
    void pendingHandoverBlocksDelivery() {
        givenHeldOrder();
        when(handovers.existsByOrder_IdAndStatusIn(order.getId(), List.of("REQUESTED", "ASSIGNED")))
                .thenReturn(true);
        assertThatThrownBy(() -> service.releaseAtDelivery(order, driver)).isInstanceOf(AppException.class);
        verify(events, never()).save(any());
    }

    @Test
    void operatorAssignmentKeepsTheCurrentDriverUntilReceipt() {
        PackageHandover handover = givenHandover("REQUESTED", null);
        var result = service.assign(handover.getId(), tenantId, operator, "OPERATOR",
                new AssignPackageHandoverRequest(receiver, null));
        assertThat(result.status()).isEqualTo("ASSIGNED");
        assertThat(order.getAssignedDriverId()).isEqualTo(driver);
        verify(readiness).requireAssignment(order.getTenant(), receiver);
    }

    @Test
    void returnTransferUpdatesTheWarehouseHandoverOwner() {
        order.setStatus(OrderStatus.RETURNING);
        PackageHandover handover = givenHandover("ASSIGNED", receiver);
        ReturnRequest request = ReturnRequest.builder().currentHolderId(driver).build();
        givenHeldOrder();
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(dispatchProvider.getObject()).thenReturn(dispatch);
        when(returns.findAllByOrder_IdAndCurrentHolderIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(order.getId()), org.mockito.ArgumentMatchers.eq(driver), any()))
                .thenReturn(List.of(request));

        var result = service.receiverConfirm(handover.getId(), tenantId, receiver, "DRIVER", null);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(request.getCurrentHolderId()).isEqualTo(receiver);
        verify(dispatch).synchronizePackageTransfer(order, driver, receiver, "RETURN");
        verify(events).save(org.mockito.ArgumentMatchers.argThat(event ->
                event.getOrder() == order && receiver.equals(event.getToCustodianId())
                        && handover.getId().equals(event.getHandoverId())));
    }

    @Test
    void deliveryTransferUpdatesPendingReturn() {
        PackageHandover handover = givenHandover("ASSIGNED", receiver);
        ReturnRequest request = ReturnRequest.builder().currentHolderId(driver).status("REQUESTED").build();
        givenHeldOrder();
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(dispatchProvider.getObject()).thenReturn(dispatch);
        when(returns.findAllByOrder_IdAndCurrentHolderIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(order.getId()), org.mockito.ArgumentMatchers.eq(driver), any()))
                .thenReturn(List.of(request));

        var result = service.receiverConfirm(handover.getId(), tenantId, receiver, "DRIVER", null);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(request.getCurrentHolderId()).isEqualTo(receiver);
        verify(dispatch).synchronizePackageTransfer(order, driver, receiver, "DELIVERY");
        verify(events).save(org.mockito.ArgumentMatchers.argThat(event ->
                event.getOrder() == order && receiver.equals(event.getToCustodianId())
                        && handover.getId().equals(event.getHandoverId())));
    }

    @Test
    void cancellingHandoverDoesNotChangeAssignment() {
        PackageHandover handover = givenHandover("ASSIGNED", receiver);
        var response = service.cancel(handover.getId(), tenantId, operator, "OPERATOR", "Đổi kế hoạch");
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(order.getAssignedDriverId()).isEqualTo(driver);
        verify(events, never()).save(any());
    }

    private void givenHeldOrder() {
        when(events.findFirstByOrder_IdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(Optional.of(PackageCustodyEvent.builder().toCustodianId(driver).build()));
    }

    private PackageHandover givenHandover(String status, UUID toDriverId) {
        PackageHandover handover = PackageHandover.builder().id(UUID.randomUUID()).order(order)
                .tenantId(tenantId).fromDriverId(driver).toDriverId(toDriverId).status(status).build();
        when(handovers.findById(handover.getId())).thenReturn(Optional.of(handover));
        return handover;
    }
}
