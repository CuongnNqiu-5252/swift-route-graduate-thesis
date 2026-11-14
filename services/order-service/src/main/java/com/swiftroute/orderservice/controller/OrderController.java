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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftroute.orderservice.dto.request.CancelOrderRequest;
import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.request.ManualAssignmentRequest;
import com.swiftroute.orderservice.dto.request.ReportIncidentRequest;
import com.swiftroute.orderservice.dto.request.StartSimulationRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.OperationsQueueResponse;
import com.swiftroute.orderservice.dto.response.OrderListPageResponse;
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
 * TenantRequestInterceptor xác thực chữ ký JWT và gắn các request attribute
 * đáng tin cậy:
 * - authorizedUserId: UUID người gọi từ JWT subject
 * - effectiveRole: Role từ JWT đã verify (SYSTEM_ADMIN, USER, OWNER,
 * TENANT_ADMIN, OPERATOR, DRIVER, VIEWER)
 * - authorizedTenantId: UUID doanh nghiệp từ JWT tenant_id (hoặc target
 * workspace của SYSTEM_ADMIN)
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
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID operatorId,
            @RequestAttribute("effectiveRole") String role,
            @Valid @RequestBody ManualAssignmentRequest request) {
        if (!java.util.Set.of("OWNER", "TENANT_ADMIN", "SYSTEM_ADMIN", "OPERATOR")
                .contains(role))
            throw new AppException(ErrorCode.ACCESS_DENIED);
        orchestration.assignManualBatch(requireTenantId(tenantId), operatorId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * GET /api/orders
     * Danh sách tất cả đơn hàng (lọc theo tenant đã xác thực và status nếu có).
     * Dùng cho Web Admin và Dispatch console.
     */
    @Operation(summary = "Lấy danh sách tất cả đơn hàng (hỗ trợ lọc status, tenant)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders(
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantUuid,
            @RequestAttribute(name = "effectiveRole", required = false) String userRole) {

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

    @GetMapping("/page")
    @Operation(summary = "Lấy danh sách đơn hàng phân trang, hỗ trợ lọc và sắp xếp theo ngày")
    public ApiResponse<OrderListPageResponse> getOrderPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID pickupLocationId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<UUID> driverIds,
            @RequestParam(defaultValue = "created_at") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        return ApiResponse.success(orderService.getOrderPage(
                tenantId, status, pickupLocationId, query, driverIds, page, size, sortBy, sortDir));
    }

    /**
     * GET /api/orders/operations/queue
     * Tenant-scoped dispatcher queues: ready to assign, active execution, and
     * intervention.
     */
    @Operation(summary = "Get tenant-scoped dispatch operations queues")
    @GetMapping("/operations/queue")
    public ResponseEntity<ApiResponse<OperationsQueueResponse>> getOperationsQueue(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole) {
        OperationsQueueResponse queue = orderService.getOperationsQueue(
                requireTenantId(tenantId), userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(queue));
    }

    /**
     * POST /api/orders
     * Tạo vận đơn giao hàng mới (Delivery Waybill).
     * Không yêu cầu tài khoản người nhận; nhận thông tin người gửi, người nhận,
     * kiện hàng và COD.
     */
    @Operation(summary = "Create a delivery waybill / order")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestAttribute("authorizedUserId") UUID creatorUserId,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute(name = "effectiveRole", required = false) String effectiveRole) {

        if ("DRIVER".equalsIgnoreCase(effectiveRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        OrderResponse response = orderService.createOrder(request, creatorUserId, tenantId, effectiveRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * GET /api/orders/{orderId}
     * Lấy chi tiết một đơn hàng.
     * Role được phép: USER (chỉ đơn của mình), OWNER, TENANT_ADMIN, OPERATOR,
     * SYSTEM_ADMIN
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable UUID orderId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {

        OrderResponse response = orderService.getOrderById(
                orderId, userId, userRole, tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/orders/{orderId}/incident
     * Driver reports an unexpected delivery incident.
     */
    @Operation(summary = "Report delivery incident — transitions order to FAILED")
    @RequestMapping(
            value = {"/{orderId}/incident", "/{orderId}/incidents"},
            method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<ApiResponse<OrderResponse>> reportIncident(
            @PathVariable UUID orderId,
            @Valid @RequestBody ReportIncidentRequest request,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole) {
        OrderResponse response = orderService.reportIncident(orderId, request, userId, userRole);
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
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole) {
        if ("DRIVER".equalsIgnoreCase(userRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        OrderResponse response = orderService.cancelOrder(orderId,
                request != null ? request : new CancelOrderRequest(null), userId, userRole);
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
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        orderService.getOrderById(orderId, userId, userRole, tenantId);
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
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        OrderResponse order = orderService.getOrderById(orderId, userId, userRole, tenantId);
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
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        orderService.getOrderById(orderId, userId, userRole, tenantId);
        trackingSimulationService.stopRouteSimulation(orderId, List.of());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "order_id", orderId,
                "status", "STOPPED")));
    }

    private UUID requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return tenantId;
    }
}
