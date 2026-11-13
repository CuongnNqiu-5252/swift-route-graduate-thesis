package com.swiftroute.orderservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.config.TenantEventConfig;
import com.swiftroute.orderservice.dto.request.CancelOrderRequest;
import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.request.ReportIncidentRequest;
import com.swiftroute.orderservice.dto.response.OperationsQueueResponse;
import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OrderEventPayload;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.PickupLocation;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PackageCategory;
import com.swiftroute.orderservice.entity.enums.PickupLocationStatus;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.PickupLocationRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toàn bộ Business Logic của Order Service (Vận đơn giao hàng bưu kiện).
 * Controller chỉ nhận request và gọi Service, không chứa business logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final PickupLocationRepository pickupLocationRepository;
    private final DeliveryZoneService deliveryZoneService;
    @org.springframework.beans.factory.annotation.Autowired
    private OperationalReadinessService operationalReadiness;

    /**
     * Tạo vận đơn giao hàng mới (Delivery Waybill).
     *
     * Phù hợp với mô hình SaaS Vận hành Giao hàng:
     * - Không bắt buộc người nhận phải có tài khoản auth.
     * - Hàng hóa được ghi nhận trực tiếp bằng mô tả và snapshot tài chính của vận đơn.
     * - Tự động sinh mã tra cứu công khai tracking_token.
     * - Trạng thái khởi tạo: PAID (sẵn sàng điều phối) để có thể lập kế hoạch tuyến
     * đường ngay.
     *
     * @param request            DTO tạo vận đơn
     * @param creatorUserId      UUID người tạo (từ JWT subject đã xác thực)
     * @param tenantIdFromHeader UUID doanh nghiệp (từ context JWT/tenant đã xác thực)
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request,
            UUID creatorUserId,
            UUID tenantIdFromHeader) {

        if (tenantIdFromHeader == null) {
            throw new AppException(ErrorCode.TENANT_NOT_FOUND);
        }

        Tenant tenant = tenantRepository.findById(tenantIdFromHeader)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new AppException(ErrorCode.TENANT_SUSPENDED);
        }
        if (tenant.getStatus() == TenantStatus.PENDING) {
            throw new AppException(ErrorCode.TENANT_PENDING);
        }
        if (tenant.getStatus() == TenantStatus.REJECTED) {
            throw new AppException(ErrorCode.TENANT_REJECTED);
        }

        operationalReadiness.requireCreation(tenant);

        // Validate time window: end must be strictly after start
        if (!request.timeWindowEnd().isAfter(request.timeWindowStart())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        // Enforce active tenant delivery zone before creating the order
        if (request.deliveryLat() != null && request.deliveryLng() != null) {
            deliveryZoneService.validateDeliveryLocation(
                    tenant.getId(),
                    request.deliveryLat(),
                    request.deliveryLng());
        }

        // Xác định điểm lấy hàng (Pickup Location)
        PickupLocation pickupLocation = null;
        Double pickupLat = request.pickupLat();
        Double pickupLng = request.pickupLng();
        String pickupAddress = request.pickupAddress();

        if (request.pickupLocationId() != null) {
            pickupLocation = pickupLocationRepository.findByIdAndTenantId(request.pickupLocationId(), tenant.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.PICKUP_LOCATION_NOT_FOUND));
            if (pickupLocation.getStatus() != PickupLocationStatus.ACTIVE) {
                throw new AppException(ErrorCode.PICKUP_LOCATION_INACTIVE);
            }
            if (pickupLat == null)
                pickupLat = pickupLocation.getLat();
            if (pickupLng == null)
                pickupLng = pickupLocation.getLng();
            if (pickupAddress == null || pickupAddress.isBlank())
                pickupAddress = pickupLocation.getAddress();
        } else {
            var defaultLocOpt = pickupLocationRepository.findByTenantIdAndIsDefaultTrueAndStatus(tenant.getId(),
                    PickupLocationStatus.ACTIVE);
            if (defaultLocOpt.isPresent()) {
                pickupLocation = defaultLocOpt.get();
                if (pickupLat == null)
                    pickupLat = pickupLocation.getLat();
                if (pickupLng == null)
                    pickupLng = pickupLocation.getLng();
                if (pickupAddress == null || pickupAddress.isBlank())
                    pickupAddress = pickupLocation.getAddress();
            }
        }

        // Fallback về tọa độ của Tenant nếu không có pickup location
        if (pickupLat == null || pickupLng == null) {
            if (tenant.getPickupLat() != null && tenant.getPickupLng() != null) {
                pickupLat = tenant.getPickupLat();
                pickupLng = tenant.getPickupLng();
                if (pickupAddress == null || pickupAddress.isBlank()) {
                    pickupAddress = tenant.getAddress();
                }
            } else {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
        }

        // Xác định thông tin người gửi
        String senderName = request.senderName();
        if (senderName == null || senderName.isBlank()) {
            senderName = (pickupLocation != null && pickupLocation.getContactName() != null)
                    ? pickupLocation.getContactName()
                    : tenant.getBusinessName();
        }

        String senderPhone = request.senderPhone();
        if (senderPhone == null || senderPhone.isBlank()) {
            senderPhone = (pickupLocation != null && pickupLocation.getContactPhone() != null)
                    ? pickupLocation.getContactPhone()
                    : "";
        }

        // Tính toán số tiền tổng (COD + phí ship nếu người nhận trả)
        BigDecimal codAmount = request.codAmount() != null ? request.codAmount() : BigDecimal.ZERO;
        BigDecimal declaredValue = request.declaredValue() != null ? request.declaredValue() : BigDecimal.ZERO;
        BigDecimal deliveryFee = request.deliveryFee() != null ? request.deliveryFee() : BigDecimal.ZERO;
        String payer = (request.payer() != null && !request.payer().isBlank()) ? request.payer().toUpperCase()
                : "SENDER";

        BigDecimal totalAmount = "RECIPIENT".equalsIgnoreCase(payer)
                ? codAmount.add(deliveryFee)
                : codAmount;

        // Sinh mã tra cứu công khai URL-safe (12 ký tự)
        String trackingToken = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        Order order = Order.builder()
                .tenant(tenant)
                .externalOrderCode(request.externalOrderCode())
                .trackingToken(trackingToken)
                .senderName(senderName)
                .senderPhone(senderPhone)
                .recipientName(request.recipientName())
                .recipientPhone(request.recipientPhone())
                .pickupLocation(pickupLocation)
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .pickupAddress(pickupAddress)
                .deliveryLat(request.deliveryLat())
                .deliveryLng(request.deliveryLng())
                .deliveryAddress(request.deliveryAddress())
                .timeWindowStart(request.timeWindowStart())
                .timeWindowEnd(request.timeWindowEnd())
                .weightKg(request.weightKg())
                .packageCount(request.packageCount())
                .priority(request.priority() != null ? request.priority() : OrderPriority.NORMAL)
                .packageCategory(request.packageCategory() != null ? request.packageCategory() : PackageCategory.PARCEL)
                .packageDescription(request.packageDescription())
                .notes(request.notes())
                .codAmount(codAmount)
                .declaredValue(declaredValue)
                .deliveryFee(deliveryFee)
                .payer(payer)
                .totalAmount(totalAmount)
                .status(OrderStatus.PAID) // Sẵn sàng để điều phối lộ trình
                .fulfillmentStatus("READY_FOR_PLANNING")
                .paymentStatus(codAmount.compareTo(BigDecimal.ZERO) > 0 ? "COD_PENDING" : "PAID")
                .codStatus(codAmount.compareTo(BigDecimal.ZERO) > 0 ? "COD_PENDING" : "NOT_APPLICABLE")
                .build();

        Order saved = orderRepository.saveAndFlush(order);
        emitOrderStatusEvent(saved, null, OrderStatus.PAID);
        log.info("Waybill created: orderId={}, tenantId={}, trackingToken={}, recipient={}, codAmount={}",
                saved.getId(), tenantIdFromHeader, trackingToken, request.recipientName(), codAmount);

        return OrderResponse.fromEntity(saved);
    }

    /**
     * Lấy chi tiết một đơn hàng theo ID.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, UUID requesterId, String requesterRole) {
        return getOrderById(orderId, requesterId, requesterRole, null);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, UUID requesterId, String requesterRole, UUID tenantIdFromContext) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        com.swiftroute.orderservice.security.TenantRequestInterceptor.assertRequestTenant(order.getTenant().getId());

        // SYSTEM_ADMIN, OWNER, TENANT_ADMIN, and OPERATOR can view all orders within scope
        if (!List.of("SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR").contains(requesterRole)) {
            boolean isTenantMember = tenantIdFromContext != null
                    && order.getTenant().getId().equals(tenantIdFromContext);
            boolean isAssignedDriver = order.getAssignedDriverId() != null
                    && order.getAssignedDriverId().equals(requesterId);
            boolean isTenantOwner = order.getTenant().getOwnerUserId() != null
                    && order.getTenant().getOwnerUserId().equals(requesterId);

            if (!isTenantMember && !isAssignedDriver && !isTenantOwner) {
                throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
            }
        }

        return OrderResponse.fromEntity(order);
    }

    /**
     * Lấy danh sách đơn hàng của Tenant (quản lý đơn trên dashboard doanh nghiệp).
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByTenant(UUID tenantId, UUID requesterId, String requesterRole) {
        assertTenantOperationsAccess(tenantId, requesterId, requesterRole);

        return orderRepository.findAllByTenantId(tenantId)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    /**
     * Returns only the operational work queues for the caller's tenant.
     */
    @Transactional(readOnly = true)
    public OperationsQueueResponse getOperationsQueue(UUID tenantId, UUID requesterId, String requesterRole) {
        assertTenantOperationsAccess(tenantId, requesterId, requesterRole);

        List<Order> orders = orderRepository.findAllByTenantIdAndStatusInOrderByUpdatedAtDesc(
                tenantId,
                List.of(OrderStatus.PAID, OrderStatus.PLANNED, OrderStatus.IN_TRANSIT,
                        OrderStatus.FAILED, OrderStatus.CANCELLED));

        return new OperationsQueueResponse(
                orders.stream().filter(order -> order.getStatus() == OrderStatus.PAID).map(OrderResponse::fromEntity)
                        .toList(),
                orders.stream()
                        .filter(order -> order.getStatus() == OrderStatus.PLANNED
                                || order.getStatus() == OrderStatus.IN_TRANSIT)
                        .map(OrderResponse::fromEntity).toList(),
                orders.stream().filter(
                        order -> order.getStatus() == OrderStatus.FAILED || order.getStatus() == OrderStatus.CANCELLED)
                        .map(OrderResponse::fromEntity).toList());
    }

    private void assertTenantOperationsAccess(UUID tenantId, UUID requesterId, String requesterRole) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
        boolean isPrivilegedRole = List.of("SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR")
                .contains(requesterRole);
        boolean isTenantOwner = tenant.getOwnerUserId() != null && tenant.getOwnerUserId().equals(requesterId);
        if (!isPrivilegedRole && !isTenantOwner) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    /**
     * Lấy danh sách đơn hàng có bộ lọc trạng thái và Tenant.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders(UUID tenantId, OrderStatus status, String requesterRole) {
        if (tenantId == null && !"SYSTEM_ADMIN".equals(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        List<Order> orders;
        if (tenantId != null && status != null) {
            orders = orderRepository.findAllByTenantIdAndStatus(tenantId, status);
        } else if (tenantId != null) {
            orders = orderRepository.findAllByTenantId(tenantId);
        } else if (status != null) {
            orders = orderRepository.findAllByStatus(status);
        } else {
            orders = orderRepository.findAll();
        }
        return orders.stream().map(OrderResponse::fromEntity).toList();
    }

    /**
     * Lấy danh sách đơn hàng đang được giao bởi tài xế.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getDriverOrders(UUID driverUserId) {
        List<OrderStatus> activeStatuses = List.of(OrderStatus.PLANNED, OrderStatus.IN_TRANSIT);
        List<Order> orders = orderRepository.findAssignedDriverOrders(driverUserId, activeStatuses);
        log.info("Found {} active orders for driver {}", orders.size(), driverUserId);
        return orders.stream().map(OrderResponse::fromEntity).toList();
    }

    /**
     * Cập nhật trạng thái đơn hàng.
     */
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus, UUID requesterId,
            String requesterRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        com.swiftroute.orderservice.security.TenantRequestInterceptor.assertRequestTenant(order.getTenant().getId());

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        validateStatusTransition(currentStatus, newStatus);

        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());

        if (newStatus == OrderStatus.IN_TRANSIT) {
            order.setFulfillmentStatus("IN_TRANSIT");
        } else if (newStatus == OrderStatus.DELIVERED) {
            order.setFulfillmentStatus("DELIVERED");
        } else if (newStatus == OrderStatus.FAILED) {
            order.setFulfillmentStatus("FAILED");
        }

        Order saved = orderRepository.save(order);
        emitOrderStatusEvent(saved, currentStatus, newStatus);
        log.info("Order {} status updated: {} -> {} by user {}", orderId, currentStatus, newStatus, requesterId);

        return OrderResponse.fromEntity(saved);
    }

    private void emitOrderStatusEvent(Order order, OrderStatus previous, OrderStatus current) {
        UUID eventId = UUID.randomUUID();
        String eventType = "order.status_changed.v1";

        OrderEventPayload payload = new OrderEventPayload(
                eventId,
                eventType,
                order.getId(),
                order.getTenant().getId(),
                order.getExternalOrderCode(),
                order.getTrackingToken(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getAssignedDriverId(),
                previous != null ? previous.name() : null,
                current.name(),
                order.getPickupLat(),
                order.getPickupLng(),
                order.getPickupAddress(),
                order.getDeliveryLat(),
                order.getDeliveryLng(),
                order.getDeliveryAddress(),
                order.getWeightKg(),
                order.getPriority() != null ? order.getPriority().name() : "NORMAL",
                order.getCodAmount() != null ? order.getCodAmount() : BigDecimal.ZERO,
                order.getTimeWindowStart(),
                order.getTimeWindowEnd(),
                Instant.now());

        outboxEventRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("order")
                .aggregateId(order.getId())
                .eventType(eventType)
                .payload(payload)
                .build());

        try {
            rabbitTemplate.convertAndSend(TenantEventConfig.EVENT_EXCHANGE, eventType, payload);
        } catch (Exception ex) {
            log.warn("Direct event publish to RabbitMQ failed for order {}, OutboxPublisher will retry: {}",
                    order.getId(), ex.getMessage());
        }
    }

    @Transactional
    public OrderResponse reportIncident(UUID orderId, ReportIncidentRequest request, UUID requesterId,
            String requesterRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        com.swiftroute.orderservice.security.TenantRequestInterceptor.assertRequestTenant(order.getTenant().getId());

        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.FAILED);
        order.setFulfillmentStatus("FAILED");

        String incidentNote = String.format("[SỰ CỐ %s]: %s (Báo cáo bởi: %s)",
                request.incidentType(), request.notes(), requesterId);
        order.setNotes(order.getNotes() != null ? order.getNotes() + " | " + incidentNote : incidentNote);
        order.setUpdatedAt(Instant.now());

        Order saved = orderRepository.save(order);
        emitOrderStatusEvent(saved, previous, OrderStatus.FAILED);
        log.warn("Incident reported for order {}: type={}, notes={}", orderId, request.incidentType(), request.notes());
        return OrderResponse.fromEntity(saved);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, CancelOrderRequest request, UUID requesterId, String requesterRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        com.swiftroute.orderservice.security.TenantRequestInterceptor.assertRequestTenant(order.getTenant().getId());

        if (order.getStatus() == OrderStatus.IN_TRANSIT || order.getStatus() == OrderStatus.DELIVERED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setFulfillmentStatus("CANCELLED");
        String cancelNote = "[HUỶ ĐƠN]: "
                + (request != null && request.reason() != null ? request.reason() : "Khách hàng huỷ đơn");
        order.setNotes(cancelNote);
        order.setUpdatedAt(Instant.now());

        Order saved = orderRepository.save(order);
        emitOrderStatusEvent(saved, previous, OrderStatus.CANCELLED);
        log.info("Order cancelled: orderId={}, reason={}", orderId, cancelNote);
        return OrderResponse.fromEntity(saved);
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PAID -> next == OrderStatus.PLANNED || next == OrderStatus.CANCELLED;
            case PLANNED ->
                next == OrderStatus.IN_TRANSIT || next == OrderStatus.FAILED || next == OrderStatus.CANCELLED;
            case IN_TRANSIT -> next == OrderStatus.DELIVERED || next == OrderStatus.FAILED;
            default -> false;
        };
        if (!valid) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }
}
