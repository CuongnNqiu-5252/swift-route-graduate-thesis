package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.AssignBatchRequest;
import com.swiftroute.orderservice.dto.request.CancelOrderRequest;
import com.swiftroute.orderservice.dto.request.ReportIncidentRequest;
import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OrderEventPayload;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PackageCategory;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toàn bộ Business Logic của Order nằm tại đây.
 * Controller chỉ nhận request và gọi Service, không chứa logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Tạo đơn hàng mới.
     *
     * Logic:
     * 1. Xác định tenantId: ưu tiên từ request body (ADMIN tạo hộ),
     * fallback về X-Tenant-Id header (Doanh nghiệp tự tạo).
     * 2. Kiểm tra Tenant tồn tại và đang hoạt động (APPROVED).
     * 3. Validate khung giờ giao hàng.
     * 4. Map DTO → Entity, lưu DB.
     *
     * @param request            DTO từ Frontend
     * @param customerId         UUID lấy từ header X-User-Id (inject bởi Gateway)
     * @param tenantIdFromHeader UUID lấy từ header X-Tenant-Id (inject bởi Gateway)
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request,
            UUID customerId,
            UUID tenantIdFromHeader) {

        // tenantId must come from the trusted X-Tenant-Id header (injected by Gateway).
        // The body no longer carries tenantId to prevent tenant spoofing.
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

        // Validate time window: end must be strictly after start
        if (!request.timeWindowEnd().isAfter(request.timeWindowStart())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        Order order = Order.builder()
                .tenant(tenant)
                .customerId(customerId)
                .pickupLat(request.pickupLat())
                .pickupLng(request.pickupLng())
                .pickupAddress(request.pickupAddress())
                .deliveryLat(request.deliveryLat())
                .deliveryLng(request.deliveryLng())
                .deliveryAddress(request.deliveryAddress())
                .timeWindowStart(request.timeWindowStart())
                .timeWindowEnd(request.timeWindowEnd())
                .weightKg(request.weightKg())
                .priority(request.priority() != null ? request.priority() : OrderPriority.NORMAL)
                .packageCategory(request.packageCategory() != null ? request.packageCategory() : PackageCategory.PARCEL)
                .itemDescription(request.itemDescription())
                .notes(request.notes())
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        Order saved = orderRepository.saveAndFlush(order);
        log.info("Order created: orderId={}, tenantId={}, customerId={}",
                saved.getId(), tenantIdFromHeader, customerId);

        return OrderResponse.fromEntity(saved);
    }

    /**
     * Lấy chi tiết một đơn hàng theo ID.
     * Kiểm tra quyền truy cập: chỉ customer tạo đơn hoặc tenant của đơn mới được
     * xem.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, UUID requesterId, String requesterRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        // ADMIN và OPERATOR được xem tất cả
        if (!requesterRole.equals("ADMIN") && !requesterRole.equals("OPERATOR")) {
            boolean isOwner = order.getCustomerId().equals(requesterId);
            boolean isTenantMember = order.getTenant().getId().equals(requesterId);
            if (!isOwner && !isTenantMember) {
                throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
            }
        }

        return OrderResponse.fromEntity(order);
    }

    /**
     * Lấy danh sách đơn hàng của Customer (xem đơn của tôi trên app).
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(UUID customerId) {
        return orderRepository.findAllByCustomerId(customerId)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    /**
     * Lấy danh sách đơn hàng của Tenant (quản lý đơn trên dashboard doanh nghiệp).
     * Chỉ ADMIN/OPERATOR/SYSTEM_ADMIN/OPS_STAFF hoặc chính chủ Tenant (ownerUserId) mới được gọi.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByTenant(UUID tenantId, UUID requesterId, String requesterRole) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        boolean isPrivilegedRole = List.of("ADMIN", "OPERATOR", "SYSTEM_ADMIN", "OPS_STAFF")
                .contains(requesterRole);
        boolean isTenantOwner = tenant.getOwnerUserId().equals(requesterId);

        if (!isPrivilegedRole && !isTenantOwner) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        return orderRepository.findAllByTenantId(tenantId)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    /**
     * Returns all orders assigned to a specific driver that are still actionable.
     * Only PLANNED and IN_TRANSIT orders are returned, sorted by delivery window start
     * so the driver sees the earliest deadline first.
     *
     * @param driverUserId the driver's auth userId from X-User-Id header
     */
    /**
     * Lấy danh sách tất cả đơn hàng (cho Web Admin / Operator), có thể lọc theo tenant hoặc status.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders(UUID tenantId, OrderStatus status, String requesterRole) {
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
        return orders.stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public List<OrderResponse> getDriverOrders(UUID driverUserId) {
        List<OrderStatus> activeStatuses = List.of(OrderStatus.PLANNED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED);
        return orderRepository.findAssignedDriverOrders(driverUserId, activeStatuses)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    /**
     * Updates the status of an order, enforcing the state machine and access control.
     *
     * Access rules:
     *  - ADMIN / SYSTEM_ADMIN / OPS_STAFF: may update any order.
     *  - Tenant owner: may update orders belonging to their tenant.
     *  - DRIVER: may only update orders where assigned_driver_id matches their userId.
     *
     * After a successful transition, an outbox event is written atomically
     * in the same transaction so the change is eventually propagated via RabbitMQ.
     */
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus, UUID requesterId, String requesterRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        // Authorization check
        boolean isPrivilegedRole = List.of("ADMIN", "SYSTEM_ADMIN", "OPS_STAFF")
                .contains(requesterRole);
        boolean isTenantOwner = order.getTenant().getOwnerUserId().equals(requesterId);
        boolean isAssignedDriver = "DRIVER".equals(requesterRole)
                && requesterId.equals(order.getAssignedDriverId());
        boolean isCustomerCompletingDelivery = "CUSTOMER".equals(requesterRole)
                && requesterId.equals(order.getCustomerId())
                && newStatus == OrderStatus.DELIVERED
                && order.getStatus() == OrderStatus.IN_TRANSIT;

        if (!isPrivilegedRole && !isTenantOwner && !isAssignedDriver && !isCustomerCompletingDelivery) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        validateStatusTransition(order.getStatus(), newStatus);
        OrderStatus previousStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());

        Order saved = orderRepository.save(order);

        // Write outbox event atomically in the same transaction.
        // The OutboxPublisher cron will pick this up and forward to RabbitMQ.
        emitOrderStatusEvent(saved, previousStatus, newStatus);

        log.info("Order status updated: orderId={}, {} -> {}", orderId, previousStatus, newStatus);
        return OrderResponse.fromEntity(saved);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Additional business operations
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Simulated payment confirmation for the demo flow.
     * Validates that the caller is the order owner or a privileged role,
     * then transitions the order from PENDING_PAYMENT to PAID.
     */
    @Transactional
    public OrderResponse confirmPayment(UUID orderId, UUID requesterId, String requesterRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        boolean isPrivileged = List.of("ADMIN", "SYSTEM_ADMIN", "OPS_STAFF").contains(requesterRole);
        boolean isOwner = order.getCustomerId().equals(requesterId);

        if (!isPrivileged && !isOwner) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        validateStatusTransition(order.getStatus(), OrderStatus.PAID);
        order.setStatus(OrderStatus.PAID);
        order.setUpdatedAt(Instant.now());
        Order saved = orderRepository.save(order);
        emitOrderStatusEvent(saved, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
        log.info("Mock payment confirmed: orderId={}", orderId);
        return OrderResponse.fromEntity(saved);
    }

    /**
     * Consumes the durable payment.completed event. Re-delivered events are safe:
     * an already PAID order is left unchanged, while any other state is rejected.
     */
    @Transactional
    public void markPaidFromPaymentEvent(UUID orderId, UUID paymentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Ignoring duplicate payment event: paymentId={}, orderId={}", paymentId, orderId);
            return;
        }
        validateStatusTransition(order.getStatus(), OrderStatus.PAID);
        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.PAID);
        order.setUpdatedAt(Instant.now());
        Order saved = orderRepository.save(order);
        emitOrderStatusEvent(saved, previous, OrderStatus.PAID);
        log.info("Payment event completed: paymentId={}, orderId={}", paymentId, orderId);
    }

    /**
     * Bulk-assigns orders from a routing plan to specific drivers.
     * Each order must be in PAID state. Atomically transitions them to PLANNED
     * and persists the assigned driver ID.
     *
     * @param request       contains list of (orderId, driverId, planId) assignments
     * @param requesterId   the requesting user's UUID
     * @param requesterRole must be ADMIN or OPS_STAFF
     */
    @Transactional
    public List<OrderResponse> assignBatch(AssignBatchRequest request, UUID requesterId, String requesterRole) {
        boolean isPrivileged = List.of("ADMIN", "SYSTEM_ADMIN", "OPS_STAFF").contains(requesterRole);
        if (!isPrivileged) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        List<OrderResponse> results = new ArrayList<>();
        for (AssignBatchRequest.AssignmentItem item : request.getEffectiveAssignments()) {
            Order order = orderRepository.findById(item.orderId())
                    .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

            // Allow re-assignment if order is already PLANNED; validate if transitioning from PAID
            if (order.getStatus() != OrderStatus.PLANNED) {
                validateStatusTransition(order.getStatus(), OrderStatus.PLANNED);
            }
            OrderStatus previous = order.getStatus();
            order.setAssignedDriverId(item.driverId());
            if (item.planId() != null) {
                order.setPlanId(item.planId());
            }
            order.setStatus(OrderStatus.PLANNED);
            order.setUpdatedAt(Instant.now());
            Order saved = orderRepository.save(order);
            emitOrderStatusEvent(saved, previous, OrderStatus.PLANNED);
            log.info("Order assigned: orderId={}, driverId={}", item.orderId(), item.driverId());
            results.add(OrderResponse.fromEntity(saved));
        }
        return results;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Writes an order.status_changed.v1 outbox event.
     * Must be called within an active @Transactional context.
     */
    private void emitOrderStatusEvent(Order order, OrderStatus previous, OrderStatus current) {
        UUID eventId = UUID.randomUUID();
        String eventType = "order.status_changed.v1";

        OrderEventPayload payload = new OrderEventPayload(
                eventId,
                eventType,
                order.getId(),
                order.getTenant().getId(),
                order.getCustomerId(),
                order.getAssignedDriverId(),
                previous.name(),
                current.name(),
                Instant.now());

        outboxEventRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("order")
                .aggregateId(order.getId())
                .eventType(eventType)
                .payload(payload)
                .build());
    }

    /**
     * State Machine: Định nghĩa các chuyển trạng thái hợp lệ.
     * Không thể nhảy từ PENDING_PAYMENT thẳng sang DELIVERED.
     */
    @Transactional
    public OrderResponse reportIncident(UUID orderId, ReportIncidentRequest request, UUID requesterId, String requesterRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        boolean isPrivileged = List.of("ADMIN", "SYSTEM_ADMIN", "OPS_STAFF").contains(requesterRole);
        boolean isAssignedDriver = "DRIVER".equals(requesterRole)
                && requesterId.equals(order.getAssignedDriverId());

        if (!isPrivileged && !isAssignedDriver) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.FAILED);
        String incidentNote = "[SỰ CỐ: " + request.incidentType() + "] " + (request.notes() != null ? request.notes() : "");
        order.setNotes(incidentNote);
        order.setUpdatedAt(Instant.now());

        Order saved = orderRepository.save(order);
        emitOrderStatusEvent(saved, previous, OrderStatus.FAILED);
        log.warn("Driver reported incident for order {}: type={}, notes={}", orderId, request.incidentType(), request.notes());
        return OrderResponse.fromEntity(saved);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, CancelOrderRequest request, UUID requesterId, String requesterRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        boolean isPrivileged = List.of("ADMIN", "SYSTEM_ADMIN", "OPS_STAFF").contains(requesterRole);
        boolean isOwner = order.getCustomerId().equals(requesterId);

        if (!isPrivileged && !isOwner) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() == OrderStatus.IN_TRANSIT || order.getStatus() == OrderStatus.DELIVERED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        String cancelNote = "[HUỶ ĐƠN]: " + (request != null && request.reason() != null ? request.reason() : "Khách hàng huỷ đơn");
        order.setNotes(cancelNote);
        order.setUpdatedAt(Instant.now());

        Order saved = orderRepository.save(order);
        emitOrderStatusEvent(saved, previous, OrderStatus.CANCELLED);
        log.info("Order cancelled: orderId={}, reason={}", orderId, cancelNote);
        return OrderResponse.fromEntity(saved);
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PENDING_PAYMENT ->
                next == OrderStatus.PAID || next == OrderStatus.PAYMENT_FAILED || next == OrderStatus.CANCELLED;
            case PAYMENT_FAILED -> next == OrderStatus.PENDING_PAYMENT || next == OrderStatus.CANCELLED;
            case PAID -> next == OrderStatus.PLANNED || next == OrderStatus.CANCELLED;
            case PLANNED -> next == OrderStatus.IN_TRANSIT || next == OrderStatus.FAILED || next == OrderStatus.CANCELLED;
            case IN_TRANSIT -> next == OrderStatus.DELIVERED || next == OrderStatus.FAILED;
            default -> false;
        };
        if (!valid) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }
}
