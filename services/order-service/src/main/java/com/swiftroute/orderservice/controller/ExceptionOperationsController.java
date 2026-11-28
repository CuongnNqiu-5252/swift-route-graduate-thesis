package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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

import com.swiftroute.orderservice.dto.request.CreateIncidentRequest;
import com.swiftroute.orderservice.dto.request.OrderReturnActionRequest;
import com.swiftroute.orderservice.dto.request.ResolveIncidentRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.DeliveryIncidentResponse;
import com.swiftroute.orderservice.dto.response.OperationsReviewResponse;
import com.swiftroute.orderservice.dto.response.PageResponse;
import com.swiftroute.orderservice.dto.response.ReturningOrderResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.service.ExceptionOperationsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
@Tag(name = "Operations & Exceptions", description = "Delivery Incident Management, Warehouse Return Check-in, and COD Reconciliation")
public class ExceptionOperationsController {

    private final ExceptionOperationsService service;

    // =========================================================================
    // 1. INCIDENTS
    // =========================================================================

    @Operation(summary = "Report an operational delivery incident")
    @PostMapping("/incidents")
    public ResponseEntity<ApiResponse<DeliveryIncidentResponse>> reportIncident(
            @Valid @RequestBody CreateIncidentRequest body,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant,
            @RequestAttribute("authorizedUserId") UUID user,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.reportIncident(body, requireTenantId(tenant), user, role)));
    }

    @Operation(summary = "List delivery incidents with optional filters")
    @GetMapping("/incidents")
    public ResponseEntity<ApiResponse<List<DeliveryIncidentResponse>>> listIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) UUID orderId,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getIncidents(requireTenantId(tenant), status, driverId, orderId)));
    }

    @Operation(summary = "List paginated delivery incidents with optional filters")
    @GetMapping("/incidents/page")
    public ApiResponse<PageResponse<DeliveryIncidentResponse>> getIncidentPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) UUID orderId,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        return ApiResponse.success(
                service.getIncidentPage(requireTenantId(tenantId), status, driverId, orderId, page, size));
    }

    @Operation(summary = "Get delivery incident details")
    @GetMapping("/incidents/{id}")
    public ResponseEntity<ApiResponse<DeliveryIncidentResponse>> getIncident(
            @PathVariable UUID id,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant) {
        return ResponseEntity.ok(ApiResponse.success(service.getIncidentById(id, requireTenantId(tenant))));
    }

    @Operation(summary = "Resolve a delivery incident with an authorized action")
    @PatchMapping("/incidents/{id}/resolve")
    public ResponseEntity<ApiResponse<DeliveryIncidentResponse>> resolveIncident(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveIncidentRequest body,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant,
            @RequestAttribute("authorizedUserId") UUID user,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                service.resolveIncident(id, requireTenantId(tenant), user, role, body)));
    }

    // =========================================================================
    // 2. WAREHOUSE RETURN CHECK-IN & RE-DISPATCH
    // =========================================================================

    @Operation(summary = "List failed, returning, or returned orders for warehouse operations")
    @GetMapping("/returning-orders")
    public ResponseEntity<ApiResponse<List<ReturningOrderResponse>>> listReturningOrders(
            @RequestParam(required = false) String status,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getReturningOrders(requireTenantId(tenant), status)));
    }

    @Operation(summary = "Confirm physical return of a failed/returning order at the warehouse")
    @PatchMapping("/orders/{orderId}/confirm-return")
    public ResponseEntity<ApiResponse<ReturningOrderResponse>> confirmOrderReturn(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) OrderReturnActionRequest req,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant,
            @RequestAttribute("authorizedUserId") UUID user,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                service.confirmOrderReturn(orderId, requireTenantId(tenant), user, role,
                        req == null ? null : req.notes())));
    }

    @Operation(summary = "Re-queue a failed or returning order back to READY_FOR_DISPATCH for the next route")
    @PatchMapping("/orders/{orderId}/requeue-dispatch")
    public ResponseEntity<ApiResponse<ReturningOrderResponse>> requeueFailedOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) OrderReturnActionRequest req,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant,
            @RequestAttribute("authorizedUserId") UUID user,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                service.requeueFailedOrder(orderId, requireTenantId(tenant), user, role,
                        req == null ? null : req.notes())));
    }

    // =========================================================================
    // 3. OPERATIONS REVIEW QUEUE
    // =========================================================================

    @Operation(summary = "Get summary of open incidents, returning orders, and pending COD batches")
    @GetMapping("/review")
    public ResponseEntity<ApiResponse<OperationsReviewResponse>> review(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenant,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(service.review(requireTenantId(tenant), role)));
    }

    private UUID requireTenantId(UUID value) {
        if (value == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return value;
    }
}
