package com.swiftroute.orderservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.OrderRepository;
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

    /**
     * Tạo đơn hàng mới.
     *
     * Logic:
     *  1. Xác định tenantId: ưu tiên từ request body (ADMIN tạo hộ),
     *     fallback về X-Tenant-Id header (Doanh nghiệp tự tạo).
     *  2. Kiểm tra Tenant tồn tại và đang hoạt động (APPROVED).
     *  3. Validate khung giờ giao hàng.
     *  4. Map DTO → Entity, lưu DB.
     *
     * @param request    DTO từ Frontend
     * @param customerId UUID lấy từ header X-User-Id (inject bởi Gateway)
     * @param tenantIdFromHeader UUID lấy từ header X-Tenant-Id (inject bởi Gateway)
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request,
                                     UUID customerId,
                                     UUID tenantIdFromHeader) {

        // Xác định tenantId cuối cùng
        UUID resolvedTenantId = (request.tenantId() != null)
                ? request.tenantId()
                : tenantIdFromHeader;

        if (resolvedTenantId == null) {
            throw new AppException(ErrorCode.TENANT_NOT_FOUND);
        }

        // Kiểm tra Tenant
        Tenant tenant = tenantRepository.findById(resolvedTenantId)
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

        // Validate time window
        if (request.timeWindowEnd().isBefore(request.timeWindowStart())
                || request.timeWindowEnd().equals(request.timeWindowStart())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        Order order = Order.builder()
                .tenant(tenant)
                .customerId(customerId)
                .pickupLat(request.pickupLat())
                .pickupLng(request.pickupLng())
                .deliveryLat(request.deliveryLat())
                .deliveryLng(request.deliveryLng())
                .deliveryAddress(request.deliveryAddress())
                .timeWindowStart(request.timeWindowStart())
                .timeWindowEnd(request.timeWindowEnd())
                .weightKg(request.weightKg())
                .priority(request.priority() != null ? request.priority() : OrderPriority.NORMAL)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created: orderId={}, tenantId={}, customerId={}",
                saved.getId(), resolvedTenantId, customerId);

        return OrderResponse.fromEntity(saved);
    }

    /**
     * Lấy chi tiết một đơn hàng theo ID.
     * Kiểm tra quyền truy cập: chỉ customer tạo đơn hoặc tenant của đơn mới được xem.
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
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByTenant(UUID tenantId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        return orderRepository.findAllByTenantId(tenantId)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    /**
     * Cập nhật trạng thái đơn hàng.
     * Áp dụng State Machine: chỉ cho phép chuyển trạng thái hợp lệ.
     */
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        validateStatusTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);

        log.info("Order status updated: orderId={}, {} → {}", orderId, order.getStatus(), newStatus);
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    /**
     * State Machine: Định nghĩa các chuyển trạng thái hợp lệ.
     * Không thể nhảy từ PENDING_PAYMENT thẳng sang DELIVERED.
     */
    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PENDING_PAYMENT -> next == OrderStatus.PAID || next == OrderStatus.PAYMENT_FAILED || next == OrderStatus.CANCELLED;
            case PAYMENT_FAILED  -> next == OrderStatus.PENDING_PAYMENT || next == OrderStatus.CANCELLED;
            case PAID            -> next == OrderStatus.PLANNED || next == OrderStatus.CANCELLED;
            case PLANNED         -> next == OrderStatus.IN_TRANSIT || next == OrderStatus.CANCELLED;
            case IN_TRANSIT      -> next == OrderStatus.DELIVERED || next == OrderStatus.FAILED;
            default              -> false;
        };
        if (!valid) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }
}
