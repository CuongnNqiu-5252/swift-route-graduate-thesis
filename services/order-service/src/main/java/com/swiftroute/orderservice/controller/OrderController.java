package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.Map;
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

import com.swiftroute.orderservice.dto.request.CancelOrderRequest;
import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.request.ReportIncidentRequest;
import com.swiftroute.orderservice.dto.request.StartSimulationRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.OperationsQueueResponse;
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
    private final com.swiftroute.orderservice.service.planning.DispatchOrchestrationService orchestration;

    @PostMapping("/assign-batch")
    @Operation(summary = "Assign ready orders through authoritative dispatch plans")
    public ResponseEntity<ApiResponse<Void>> assignBatch(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID operatorId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody com.swiftroute.orderservice.dto.request.ManualAssignmentRequest request) {
        if (!java.util.Set.of("OWNER", "TENANT_ADMIN", "ADMIN", "SYSTEM_ADMIN", "OPS_STAFF", "MANAGER", "OPERATOR").contains(role))
            throw new AppException(ErrorCode.ACCESS_DENIED);
        orchestration.assignManualBatch(tenantId, operatorId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

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
     * GET /api/orders/operations/queue
     * Tenant-scoped dispatcher queues: ready to assign, active execution, and intervention.
     */
    @Operation(summary = "Get tenant-scoped dispatch operations queues")
    @GetMapping("/operations/queue")
    public ResponseEntity<ApiResponse<OperationsQueueResponse>> getOperationsQueue(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        OperationsQueueResponse queue = orderService.getOperationsQueue(
                UUID.fromString(tenantId), UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(queue));
    }

    /**
     * POST /api/orders
     * Tạo vận đơn giao hàng mới (Delivery Waybill).
     * Không yêu cầu tài khoản người nhận; nhận thông tin người gửi, người nhận, kiện hàng và COD.
     */
    @Operation(summary = "Create a delivery waybill / order")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        UUID creatorUserId = (userId != null && !userId.isBlank()) ? UUID.fromString(userId) : null;
        UUID tenantIdUUID = (tenantId != null && !tenantId.isBlank())
                ? UUID.fromString(tenantId)
                : null;

        OrderResponse response = orderService.createOrder(request, creatorUserId, tenantIdUUID);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * GET /api/orders/track/{trackingToken}
     * Tra cứu vận đơn công khai cho người nhận qua mã trackingToken.
     * Không yêu cầu JWT đăng nhập.
     */
    @Operation(summary = "Track delivery order publicly by tracking token")
    @GetMapping("/track/{trackingToken}")
    public ResponseEntity<ApiResponse<OrderResponse>> trackOrderByToken(
            @PathVariable String trackingToken) {

        OrderResponse response = orderService.getOrderByTrackingToken(trackingToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/orders/driver/assigned
     * Returns the active orders assigned to the authenticated driver,
     * sorted by delivery window start (earliest first).
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
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader) {

        UUID tenantId = (tenantIdHeader != null && !tenantIdHeader.isBlank())
                ? UUID.fromString(tenantIdHeader) : null;
        OrderResponse response = orderService.getOrderById(
                orderId, UUID.fromString(userId), userRole, tenantId);
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
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody Object rawBody,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        OrderStatus newStatus;
        if (rawBody instanceof Map<?, ?> map && map.containsKey("status")) {
            newStatus = OrderStatus.valueOf(map.get("status").toString().trim());
        } else if (rawBody instanceof String str) {
            newStatus = OrderStatus.valueOf(str.replace("\"", "").trim());
        } else {
            newStatus = OrderStatus.valueOf(rawBody.toString().replace("\"", "").trim());
        }

        OrderResponse response = orderService.updateOrderStatus(orderId, newStatus, UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/orders/{orderId}/incident
     * Driver reports an unexpected delivery incident.
     */
    @Operation(summary = "Report delivery incident — transitions order to FAILED")
    @PatchMapping("/{orderId}/incident")
    public ResponseEntity<ApiResponse<OrderResponse>> reportIncident(
            @PathVariable UUID orderId,
            @Valid @RequestBody ReportIncidentRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        OrderResponse response = orderService.reportIncident(orderId, request, UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/orders/{orderId}/cancel
     * Cancels an order before it is dispatched or delivered.
     */
    @Operation(summary = "Cancel order — transitions order to CANCELLED")
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable UUID orderId,
            @RequestBody(required = false) CancelOrderRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        OrderResponse response = orderService.cancelOrder(orderId, request != null ? request : new CancelOrderRequest(null), UUID.fromString(userId), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/orders/{orderId}/tracking/stream
     * Server-Sent Events (SSE) live real-time location stream.
     */
    @Operation(summary = "Subscribe to live real-time SSE tracking stream for an order")
    @GetMapping(value = "/{orderId}/tracking/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderTracking(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader) {
        UUID tenantId = tenantIdHeader == null || tenantIdHeader.isBlank() ? null : UUID.fromString(tenantIdHeader);
        orderService.getOrderById(orderId, UUID.fromString(userId), userRole, tenantId);
        return trackingSimulationService.subscribe(orderId);
    }

    /**
     * POST /api/orders/{orderId}/tracking/simulate
     * Starts an on-demand GPS route simulation along the given street waypoints.
     */
    @Operation(summary = "Start on-demand GPS route simulation for an order")
    @PostMapping("/{orderId}/tracking/simulate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startOrderSimulation(
            @PathVariable UUID orderId,
            @Valid @RequestBody StartSimulationRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader) {
        UUID tenantId = tenantIdHeader == null || tenantIdHeader.isBlank() ? null : UUID.fromString(tenantIdHeader);
        OrderResponse order = orderService.getOrderById(orderId, UUID.fromString(userId), userRole, tenantId);
        trackingSimulationService.startRouteSimulation(
                orderId,
                order.assignedDriverId(),
                List.of(),
                request.waypoints(),
                request.intervalMs());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "order_id", orderId,
                "waypoint_count", request.waypoints().size(),
                "status", "SIMULATING")));
    }

    /**
     * POST /api/orders/{orderId}/tracking/simulate/stop
     * Stops an active GPS route simulation for an order.
     */
    @Operation(summary = "Stop on-demand GPS route simulation for an order")
    @PostMapping("/{orderId}/tracking/simulate/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopOrderSimulation(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader) {
        UUID tenantId = tenantIdHeader == null || tenantIdHeader.isBlank() ? null : UUID.fromString(tenantIdHeader);
        orderService.getOrderById(orderId, UUID.fromString(userId), userRole, tenantId);
        trackingSimulationService.stopRouteSimulation(orderId, List.of());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "order_id", orderId,
                "status", "STOPPED")));
    }
}
