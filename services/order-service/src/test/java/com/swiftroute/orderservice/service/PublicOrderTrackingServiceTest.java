package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.swiftroute.orderservice.dto.response.PublicOrderTrackingResponse;
import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OrderStatusHistory;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PackageCategory;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OrderStatusHistoryRepository;
import com.swiftroute.orderservice.security.JwtTokenValidator;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;

@ExtendWith(MockitoExtension.class)
class PublicOrderTrackingServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock private DeliveryAttemptRepository deliveryAttemptRepository;

    @InjectMocks private PublicOrderTrackingService service;

    @Test
    void masksPiiAndBuildsCustomerSafeMilestonesWithoutInternalNotes() {
        UUID orderId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .businessName("Cửa hàng Hoa Tươi")
                .build();
        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .externalOrderCode("ORD-888")
                .trackingToken("TRK123456789")
                .senderName("Tran Thi Hoa")
                .pickupAddress("12 Nguyen Trai, Phường 3, Quận 5, TP.HCM")
                .recipientName("Nguyen Van Anh")
                .recipientPhone("0901234567")
                .deliveryAddress("123 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM")
                .packageCategory(PackageCategory.PARCEL)
                .packageDescription("Bó hoa sinh nhật")
                .weightKg(1.2)
                .codAmount(new BigDecimal("250000"))
                .codStatus("HANDED_OVER")
                .status(OrderStatus.DELIVERED)
                .createdAt(Instant.parse("2026-11-20T01:00:00Z"))
                .updatedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(orderRepository.findByTrackingToken("TRK123456789")).thenReturn(Optional.of(order));
        when(statusHistoryRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)).thenReturn(List.of(
                OrderStatusHistory.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .tenantId(tenant.getId())
                        .fromStatus("IN_TRANSIT")
                        .toStatus("DELIVERED")
                        .reason("Ghi chú nội bộ: khách hơi khó tính")
                        .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                        .build()));
        when(deliveryAttemptRepository.findAllByOrder_IdOrderByCompletedAtDesc(orderId)).thenReturn(List.of(
                DeliveryAttempt.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .attemptNumber(2)
                        .outcome("SUCCESS")
                        .notes("Ghi chú tài xế nội bộ")
                        .proofUrl("https://cdn.example.com/pod-888.jpg")
                        .completedAt(Instant.now().minus(65, ChronoUnit.MINUTES))
                        .build(),
                DeliveryAttempt.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .attemptNumber(1)
                        .outcome("FAILED")
                        .failureReason("CUSTOMER_UNREACHABLE")
                        .notes("Gọi 3 lần không bắt máy - nội bộ")
                        .completedAt(Instant.now().minus(4, ChronoUnit.HOURS))
                        .build()));

        PublicOrderTrackingResponse response = service.getByTrackingToken(" trk123456789 ");

        assertThat(response.trackingToken()).isEqualTo("TRK123456789");
        assertThat(response.orderCode()).isEqualTo("ORD-888");
        assertThat(response.carrierName()).isEqualTo("Cửa hàng Hoa Tươi");
        assertThat(response.progressStep()).isEqualTo(4);
        assertThat(response.maskedSenderName()).isEqualTo("Tran T. H.");
        assertThat(response.maskedPickupArea()).isEqualTo("Quận 5, TP.HCM");
        assertThat(response.maskedRecipientName()).isEqualTo("Nguyen V. A.");
        assertThat(response.maskedRecipientPhone()).isEqualTo("090***567");
        assertThat(response.maskedDeliveryArea()).isEqualTo("Quận 1, TP.HCM");
        assertThat(response.latestProofUrl()).isEqualTo("https://cdn.example.com/pod-888.jpg");

        assertThat(response.milestones()).hasSize(3);
        assertThat(response.milestones())
                .noneMatch(m -> m.description() != null && m.description().contains("nội bộ"));
        assertThat(response.milestones().stream()
                .filter(m -> "DELIVERY_ATTEMPT_FAILED".equals(m.eventType()))
                .findFirst()
                .orElseThrow()
                .description()).isEqualTo("Chưa liên lạc được với người nhận");
    }

    @Test
    void rejectsExpiredTerminalOrderOlderThanTtl() {
        Order expired = Order.builder()
                .id(UUID.randomUUID())
                .trackingToken("EXPIRED123456")
                .status(OrderStatus.DELIVERED)
                .createdAt(Instant.now().minus(70, ChronoUnit.DAYS))
                .updatedAt(Instant.now().minus(65, ChronoUnit.DAYS))
                .build();

        when(orderRepository.findByTrackingToken("EXPIRED123456")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.getByTrackingToken("EXPIRED123456"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void allowsPublicTrackingPathInTenantRequestInterceptorWithoutAuthHeader() {
        TenantRequestInterceptor interceptor = new TenantRequestInterceptor(mock(JwtTokenValidator.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/tracking/TRK123456");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }
}
