package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller xử lý API cho Order.
 *
 * Quan trọng: Controller này KHÔNG xử lý JWT.
 * Toàn bộ xác thực đã được API Gateway làm.
 * Controller chỉ đọc các header đã được Gateway inject:
 *  - X-User-Id    : UUID của người đang gọi API
 *  - X-User-Role  : Role (CUSTOMER, DRIVER, ADMIN, OPERATOR)
 *  - X-Tenant-Id  : UUID của Tenant (nếu user thuộc về Tenant)
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/orders
     * Tạo đơn hàng mới.
     * Role được phép: CUSTOMER, ADMIN, OPERATOR (Doanh nghiệp tạo hộ khách)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        UUID customerId = UUID.fromString(userId);
        UUID tenantIdUUID = (tenantId != null && !tenantId.isBlank())
                ? UUID.fromString(tenantId)
                : null;

        OrderResponse response = orderService.createOrder(request, customerId, tenantIdUUID);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * GET /api/orders/me
     * Lấy danh sách đơn hàng của người dùng hiện tại.
     * Role được phép: CUSTOMER
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @RequestHeader("X-User-Id") String userId) {

        List<OrderResponse> orders = orderService.getMyOrders(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    /**
     * GET /api/orders/{orderId}
     * Lấy chi tiết một đơn hàng.
     * Role được phép: CUSTOMER (chỉ đơn của mình), ADMIN, OPERATOR
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        OrderResponse response = orderService.getOrderById(
                orderId, UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/orders/tenant/{tenantId}
     * Lấy danh sách đơn hàng của một Tenant.
     * Role được phép: ADMIN, OPERATOR
     */
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByTenant(
            @PathVariable UUID tenantId) {

        List<OrderResponse> orders = orderService.getOrdersByTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    /**
     * PATCH /api/orders/{orderId}/status
     * Cập nhật trạng thái đơn hàng.
     * Role được phép: ADMIN, OPERATOR, DRIVER (qua routing engine sau này)
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody OrderStatus newStatus) {

        OrderResponse response = orderService.updateOrderStatus(orderId, newStatus);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
