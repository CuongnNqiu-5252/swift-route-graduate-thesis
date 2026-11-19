package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.response.DeliveryZoneCheckResponse;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDeliveryZone;
import com.swiftroute.orderservice.entity.enums.DeliveryZoneEnforcementMode;
import com.swiftroute.orderservice.entity.enums.DeliveryZoneType;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.PackageCategory;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.AuditLogRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.PickupLocationRepository;
import com.swiftroute.orderservice.repository.TenantDeliveryZoneRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class DeliveryZoneServiceTest {

    @Mock
    private TenantDeliveryZoneRepository deliveryZoneRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private DeliveryZoneService deliveryZoneService;

    private UUID tenantId;
    private Tenant tenant;
    private TenantDeliveryZone activeStrictZone;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(tenantId)
                .businessName("Can Tho Express")
                .status(TenantStatus.APPROVED)
                .pickupLat(10.03)
                .pickupLng(105.78)
                .address("123 Ninh Kieu, Can Tho")
                .build();

        activeStrictZone = TenantDeliveryZone.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .zoneType(DeliveryZoneType.CIRCLE)
                .centerLat(10.03)
                .centerLng(105.78)
                .radiusKm(5.0)
                .enforcementMode(DeliveryZoneEnforcementMode.STRICT_BLOCK)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("checkLocation allows any point when zone is not configured")
    void checkLocationUnconfigured() {
        when(deliveryZoneRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        DeliveryZoneCheckResponse response = deliveryZoneService.checkLocation(tenantId, 10.77, 106.70);

        assertThat(response.configured()).isFalse();
        assertThat(response.allowed()).isTrue();
        assertThat(response.inZone()).isTrue();
    }

    @Test
    @DisplayName("checkLocation blocks outside point in STRICT_BLOCK mode")
    void checkLocationStrictBlockOutside() {
        when(deliveryZoneRepository.findByTenantId(tenantId)).thenReturn(Optional.of(activeStrictZone));

        // Ho Chi Minh City (~125 km away from Can Tho center)
        DeliveryZoneCheckResponse response = deliveryZoneService.checkLocation(tenantId, 10.7769, 106.7009);

        assertThat(response.configured()).isTrue();
        assertThat(response.inZone()).isFalse();
        assertThat(response.allowed()).isFalse();
        assertThat(response.distanceKm()).isGreaterThan(5.0);
        assertThat(response.enforcementMode()).isEqualTo(DeliveryZoneEnforcementMode.STRICT_BLOCK);
    }

    @Test
    @DisplayName("checkLocation allows outside point in WARN_ONLY mode")
    void checkLocationWarnOnlyOutside() {
        activeStrictZone.setEnforcementMode(DeliveryZoneEnforcementMode.WARN_ONLY);
        when(deliveryZoneRepository.findByTenantId(tenantId)).thenReturn(Optional.of(activeStrictZone));

        DeliveryZoneCheckResponse response = deliveryZoneService.checkLocation(tenantId, 10.7769, 106.7009);

        assertThat(response.configured()).isTrue();
        assertThat(response.inZone()).isFalse();
        assertThat(response.allowed()).isTrue();
        assertThat(response.enforcementMode()).isEqualTo(DeliveryZoneEnforcementMode.WARN_ONLY);
    }

    @Test
    @DisplayName("checkLocation allows point inside radius")
    void checkLocationInsideZone() {
        when(deliveryZoneRepository.findByTenantId(tenantId)).thenReturn(Optional.of(activeStrictZone));

        DeliveryZoneCheckResponse response = deliveryZoneService.checkLocation(tenantId, 10.035, 105.785);

        assertThat(response.configured()).isTrue();
        assertThat(response.inZone()).isTrue();
        assertThat(response.allowed()).isTrue();
        assertThat(response.distanceKm()).isLessThanOrEqualTo(5.0);
    }

    @Test
    @DisplayName("validateDeliveryLocation throws DELIVERY_OUT_OF_ZONE when STRICT_BLOCK and outside radius")
    void validateDeliveryLocationThrowsStrictBlock() {
        when(deliveryZoneRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(activeStrictZone));

        assertThatThrownBy(() -> deliveryZoneService.validateDeliveryLocation(tenantId, 10.7769, 106.7009))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_OUT_OF_ZONE);
    }

    @Test
    @DisplayName("validateDeliveryLocation allows outside point when WARN_ONLY")
    void validateDeliveryLocationAllowsWarnOnly() {
        activeStrictZone.setEnforcementMode(DeliveryZoneEnforcementMode.WARN_ONLY);
        when(deliveryZoneRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(Optional.of(activeStrictZone));

        assertThatCode(() -> deliveryZoneService.validateDeliveryLocation(tenantId, 10.7769, 106.7009))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OrderService.createOrder rejects order before saving when delivery location is out of zone")
    void orderServiceRejectsOutOfZoneOrder() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        PackageCustodyService custody = mock(PackageCustodyService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        PickupLocationRepository pickupLocationRepository = mock(PickupLocationRepository.class);
        DeliveryZoneService zoneServiceMock = mock(DeliveryZoneService.class);
        OperationalReadinessService readiness = mock(OperationalReadinessService.class);

        OrderService orderService = new OrderService(
                orderRepository,
                custody,
                tenantRepository,
                outboxEventRepository,
                rabbitTemplate,
                pickupLocationRepository,
                zoneServiceMock);
        ReflectionTestUtils.setField(orderService, "operationalReadiness", readiness);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        doThrow(new AppException(ErrorCode.DELIVERY_OUT_OF_ZONE))
                .when(zoneServiceMock)
                .validateDeliveryLocation(tenantId, 10.7769, 106.7009);

        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
        OffsetDateTime end = start.plusHours(4);
        CreateOrderRequest request = new CreateOrderRequest(
                null,
                null,
                null,
                "Nguyen Van B",
                "0901234567",
                null,
                10.03,
                105.78,
                "123 Ninh Kieu",
                10.7769,
                106.7009,
                "Quan 1, TP.HCM",
                start,
                end,
                1.5,
                PackageCategory.PARCEL,
                null,
                null,
                OrderPriority.NORMAL,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "SENDER");

        assertThatThrownBy(() -> orderService.createOrder(request, UUID.randomUUID(), tenantId, "OWNER"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_OUT_OF_ZONE);

        verify(orderRepository, never()).saveAndFlush(any());
    }
}
