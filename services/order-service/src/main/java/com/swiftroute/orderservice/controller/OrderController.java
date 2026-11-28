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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftroute.orderservice.dto.request.CancelOrderRequest;
import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.request.ManualAssignmentRequest;
import com.swiftroute.orderservice.dto.request.UpdateOrderRequest;
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
        if (!com.swiftroute.orderservice.security.TenantRequestInterceptor.isAuthorizedForPermission(
                role,
                "dispatch:execute",
                java.util.Set.of("OWNER", "TENANT_ADMIN", "SYSTEM_ADMIN", "OPERATOR", "DRIVER"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        orchestration.assignManualBatch(requireTenantId(tenantId), operatorId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

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

    @Operation(summary = "Create a delivery waybill / order")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestAttribute("authorizedUserId") UUID creatorUserId,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute(name = "effectiveRole", required = false) String effectiveRole) {

        if (!com.swiftroute.orderservice.security.TenantRequestInterceptor.isAuthorizedForPermission(
                effectiveRole,
                "order:create",
                java.util.Set.of("OWNER", "TENANT_ADMIN", "SYSTEM_ADMIN", "OPERATOR", "USER"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        OrderResponse response = orderService.createOrder(request, creatorUserId, tenantId, effectiveRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order details by ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable UUID orderId,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {

        OrderResponse response = orderService.getOrderById(
                orderId, userId, userRole, tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Cancel order — transitions order to CANCELLED")
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable UUID orderId,
            @RequestBody(required = false) CancelOrderRequest request,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole) {
        if (!com.swiftroute.orderservice.security.TenantRequestInterceptor.isAuthorizedForPermission(
                userRole,
                "order:cancel",
                java.util.Set.of("OWNER", "TENANT_ADMIN", "SYSTEM_ADMIN", "OPERATOR", "USER"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        OrderResponse response = orderService.cancelOrder(orderId,
                request != null ? request : new CancelOrderRequest(null), userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Partially update an order before it enters transit")
    @PatchMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderRequest request,
            @RequestAttribute("authorizedUserId") UUID userId,
            @RequestAttribute("effectiveRole") String userRole) {
        if (!com.swiftroute.orderservice.security.TenantRequestInterceptor.isAuthorizedForPermission(
                userRole,
                "order:create",
                java.util.Set.of("OWNER", "TENANT_ADMIN", "SYSTEM_ADMIN", "OPERATOR", "USER"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        OrderResponse response = orderService.updateOrder(orderId, request, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

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

    private UUID requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return tenantId;
    }
}
