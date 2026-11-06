package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.PackageUnit;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.PackageCustodyEventRepository;
import com.swiftroute.orderservice.repository.PackageHandoverRepository;
import com.swiftroute.orderservice.repository.PackageUnitRepository;

@ExtendWith(MockitoExtension.class)
class PackageCustodyServiceTest {

    @Mock
    private PackageUnitRepository packageUnits;

    @Mock
    private PackageCustodyEventRepository custodyEvents;

    @Mock
    private PackageHandoverRepository handovers;

    @Mock
    private DeliveryTaskRepository deliveryTasks;

    @Mock
    private OrderRepository orders;

    @InjectMocks
    private PackageCustodyService service;

    private UUID tenantId;
    private UUID driverId;
    private Order order;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).businessName("Shop HCM").build();
        order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .trackingToken("TRK12345678")
                .packageCount(1)
                .assignedDriverId(driverId)
                .build();
    }

    @Test
    void acquireAtPickup_createsUnitsAndAcquiresWhenNoneExist() {
        when(packageUnits.findByOrder_IdOrderByPackageNumberAsc(order.getId())).thenReturn(new ArrayList<>());
        when(packageUnits.save(any(PackageUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.acquireAtPickup(order, driverId);

        verify(packageUnits).save(any(PackageUnit.class));
        verify(custodyEvents).save(any());
    }

    @Test
    void acquireAtPickup_idempotentWhenDriverAlreadyHasCustody() {
        PackageUnit unit = PackageUnit.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenantId(tenantId)
                .packageNumber(1)
                .packageCode("TRK12345678-P01")
                .currentCustodianId(driverId)
                .status("IN_CUSTODY")
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();

        when(packageUnits.findByOrder_IdOrderByPackageNumberAsc(order.getId())).thenReturn(List.of(unit));

        service.acquireAtPickup(order, driverId);

        assertThat(unit.getCurrentCustodianId()).isEqualTo(driverId);
        assertThat(unit.getStatus()).isEqualTo("IN_CUSTODY");
        verify(packageUnits, never()).save(any(PackageUnit.class));
    }

    @Test
    void acquireAtPickup_throwsWhenHeldByOtherDriver() {
        UUID otherDriverId = UUID.randomUUID();
        PackageUnit unit = PackageUnit.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenantId(tenantId)
                .packageNumber(1)
                .packageCode("TRK12345678-P01")
                .currentCustodianId(otherDriverId)
                .status("IN_CUSTODY")
                .build();

        when(packageUnits.findByOrder_IdOrderByPackageNumberAsc(order.getId())).thenReturn(List.of(unit));

        assertThatThrownBy(() -> service.acquireAtPickup(order, driverId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
    }

    @Test
    void acquireAtPickup_throwsWhenUnitIsDeliveredOrReturned() {
        PackageUnit deliveredUnit = PackageUnit.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenantId(tenantId)
                .packageNumber(1)
                .packageCode("TRK12345678-P01")
                .currentCustodianId(null)
                .status("DELIVERED")
                .build();

        when(packageUnits.findByOrder_IdOrderByPackageNumberAsc(order.getId())).thenReturn(List.of(deliveredUnit));

        assertThatThrownBy(() -> service.acquireAtPickup(order, driverId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
    }
}

