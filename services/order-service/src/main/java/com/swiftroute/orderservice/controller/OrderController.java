package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftroute.orderservice.dto.request.AssignBatchRequest;
import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.service.OrderService;
import com.swiftroute.orderservice.service.TrackingSimulationService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller xử lý API cho Order.
 *
 * Quan trọng: Controller này KHÔNG xử lý JWT.
 * Toàn bộ xác thực đã được API Gateway làm.
 * Controller chỉ đọc các header đã được Gateway inject:
 * - X-User-Id : UUID của người đang gọi API
 * - X-User-Role : Role (CUSTOMER, DRIVER, ADMIN, OPERATOR)
 * - X-Tenant-Id : UUID của Tenant (nếu user thuộc về Tenant)
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final TrackingSimulationService trackingSimulationService;

    /**
     * GET /api/orders
     * Danh sách tất cả đơn hàng (hỗ trợ lọc theo status, tenant).
     * Dùng cho Web Admin và Dispatch console.
     */
    @Operation(summary = "Lấy danh sách tất cả đơn hàng (hỗ trợ lọc status, tenant)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders(
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestParam(value = "tenant_id", required = false) String tenantIdParam,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "ADMIN") String userRole) {

        String effectiveTenant = (tenantIdHeader != null && !tenantIdHeader.isBlank())
                ? tenantIdHeader
                : tenantIdParam;

        UUID tenantUuid = (effectiveTenant != null && !effectiveTenant.isBlank())
                ? UUID.fromString(effectiveTenant)
                : null;

        OrderStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = OrderStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        List<OrderResponse> orders = orderService.getAllOrders(tenantUuid, status, userRole);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

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
                : UUID.fromString("a1111111-1111-1111-1111-111111111111");

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
     * GET /api/orders/driver/assigned
     * Returns the active orders assigned to the authenticated driver,
     * sorted by delivery window start (earliest first).
     *
     * The driver's identity is read from X-User-Id (injected by Gateway after JWT verification).
     * Only orders in PLANNED or IN_TRANSIT state are returned.
     * Role required: DRIVER
     */
    @Operation(summary = "Get active orders assigned to the authenticated driver")
    @GetMapping("/driver/assigned")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getDriverOrders(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"DRIVER".equals(userRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        List<OrderResponse> orders = orderService.getDriverOrders(UUID.fromString(userId));
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
            @PathVariable UUID tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        List<OrderResponse> orders = orderService.getOrdersByTenant(tenantId, UUID.fromString(userId), userRole);
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
            @RequestBody OrderStatus newStatus,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        OrderResponse response = orderService.updateOrderStatus(orderId, newStatus, UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/orders/{orderId}/pay
     * Simulated payment confirmation for demo purposes.
     * Transitions order from PENDING_PAYMENT to PAID.
     * Allowed roles: CUSTOMER (owner of the order only), ADMIN, OPS_STAFF.
     */
    @Operation(summary = "Confirm mock payment — transitions order to PAID")
    @PatchMapping("/{orderId}/pay")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmPayment(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        OrderResponse response = orderService.confirmPayment(orderId, UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/orders/assign-batch
     * Bulk-assigns a list of optimised route stops to drivers.
     * Transitions each order from PAID to PLANNED and records assigned_driver_id.
     * Allowed roles: ADMIN, OPS_STAFF.
     */
    @Operation(summary = "Assign optimised batch plan to drivers, transitioning orders to PLANNED")
    @PostMapping("/assign-batch")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> assignBatch(
            @Valid @RequestBody AssignBatchRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        List<OrderResponse> responses = orderService.assignBatch(request, UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * GET /api/orders/{orderId}/tracking/stream
     * Server-Sent Events (SSE) live real-time location stream.
     * Streams autonomous simulated waypoints towards destination or real GPS updates.
     */
    @Operation(summary = "Subscribe to live real-time SSE tracking stream for an order")
    @GetMapping(value = "/{orderId}/tracking/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderTracking(
            @PathVariable UUID orderId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return trackingSimulationService.subscribe(orderId);
    }
}
