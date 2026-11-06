package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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

import com.swiftroute.orderservice.dto.request.ApproveReturnRequest;
import com.swiftroute.orderservice.dto.request.CodHandoverRequest;
import com.swiftroute.orderservice.dto.request.CollectCodRequest;
import com.swiftroute.orderservice.dto.request.ConfirmReturnReceiptRequest;
import com.swiftroute.orderservice.dto.request.CreateIncidentRequest;
import com.swiftroute.orderservice.dto.request.CreateReturnRequest;
import com.swiftroute.orderservice.dto.request.ReportReturnDiscrepancyRequest;
import com.swiftroute.orderservice.dto.request.ResolveIncidentRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.DeliveryIncidentResponse;
import com.swiftroute.orderservice.dto.response.OperationsReviewResponse;
import com.swiftroute.orderservice.dto.response.ReturnRequestResponse;
import com.swiftroute.orderservice.service.ExceptionOperationsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
@Tag(name = "Operations & Exceptions", description = "Delivery Incident Management, Return-to-Sender (RTS), and COD Reconciliation")
public class ExceptionOperationsController {

    private final ExceptionOperationsService service;

    // =========================================================================
    // 1. INCIDENTS
    // =========================================================================

    @Operation(summary = "Report an operational delivery incident (order-level or route-level)")
    @PostMapping("/incidents")
    public ResponseEntity<ApiResponse<DeliveryIncidentResponse>> reportIncident(
            @Valid @RequestBody CreateIncidentRequest body,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.reportIncident(body, id(tenant), id(user), role)));
    }

    @Operation(summary = "Report an incident for a specific order (legacy/convenience endpoint)")
    @PostMapping("/orders/{orderId}/incidents")
    public ResponseEntity<ApiResponse<DeliveryIncidentResponse>> reportOrderIncident(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateIncidentRequest body,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        CreateIncidentRequest enriched = new CreateIncidentRequest(
                orderId,
                body.dispatchPlanId(),
                body.incidentType(),
                body.severity(),
                body.notes(),
                body.evidenceUrls(),
                body.canContinueDelivery(),
                body.needsImmediateAssistance(),
                body.contactAttempts());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.reportIncident(enriched, id(tenant), id(user), role)));
    }

    @Operation(summary = "List delivery incidents with optional filters")
    @GetMapping("/incidents")
    public ResponseEntity<ApiResponse<List<DeliveryIncidentResponse>>> listIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) UUID orderId,
            @RequestHeader("X-Tenant-Id") String tenant) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getIncidents(id(tenant), status, severity, driverId, orderId)));
    }

    @Operation(summary = "Get delivery incident details")
    @GetMapping("/incidents/{id}")
    public ResponseEntity<ApiResponse<DeliveryIncidentResponse>> getIncident(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-Id") String tenant) {
        return ResponseEntity.ok(ApiResponse.success(service.getIncidentById(id, id(tenant))));
    }

    @Operation(summary = "Resolve a delivery incident with an authorized action")
    @PatchMapping("/incidents/{id}/resolve")
    public ResponseEntity<ApiResponse<DeliveryIncidentResponse>> resolveIncident(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveIncidentRequest body,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                service.resolveIncident(id, id(tenant), id(user), role, body)));
    }

    // =========================================================================
    // 2. RETURN-TO-SENDER (RTS)
    // =========================================================================

    @Operation(summary = "Create a Return Request for an order")
    @PostMapping("/returns")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> createReturn(
            @Valid @RequestBody CreateReturnRequest body,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.createReturnRequest(id(tenant), id(user), role, body)));
    }

    @Operation(summary = "Create a Return Request for a specific order (convenience endpoint)")
    @PostMapping("/orders/{orderId}/returns")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> createOrderReturn(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateReturnRequest body,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        CreateReturnRequest enriched = new CreateReturnRequest(
                orderId,
                body.incidentId(),
                body.requestType(),
                body.reason(),
                body.returnLocationId(),
                body.packageCount(),
                body.notes(),
                body.evidenceUrl());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.createReturnRequest(id(tenant), id(user), role, enriched)));
    }

    @Operation(summary = "List return requests with optional filters")
    @GetMapping("/returns")
    public ResponseEntity<ApiResponse<List<ReturnRequestResponse>>> listReturns(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID driverId,
            @RequestHeader("X-Tenant-Id") String tenant) {
        return ResponseEntity.ok(ApiResponse.success(service.getReturnRequests(id(tenant), status, driverId)));
    }

    @Operation(summary = "Get return request details")
    @GetMapping("/returns/{id}")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> getReturn(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-Id") String tenant) {
        return ResponseEntity.ok(ApiResponse.success(service.getReturnRequestById(id, id(tenant))));
    }

    @Operation(summary = "Approve Return Request and assign return task")
    @PatchMapping("/returns/{id}/approve")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> approveReturn(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveReturnRequest body,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                service.approveReturn(id, id(tenant), id(user), role, body)));
    }

    @Operation(summary = "Driver confirms arrival at return destination warehouse")
    @PatchMapping("/returns/{id}/arrive")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> arriveReturn(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                service.arriveReturnLocation(id, id(tenant), id(user), role)));
    }

    @Operation(summary = "Driver initiates package handover to warehouse staff")
    @PatchMapping("/returns/{id}/handover")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> handoverReturn(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                service.initiateReturnHandover(id, id(tenant), id(user), role)));
    }

    @Operation(summary = "Warehouse receiving employee confirms physical receipt of returned goods")
    @PatchMapping("/returns/{id}/confirm-receipt")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> confirmReturnReceipt(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmReturnReceiptRequest req,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                service.confirmReturnReceipt(id, id(tenant), id(user), role, req == null ? null : req.notes())));
    }

    @Operation(summary = "Warehouse employee reports discrepancy (damaged or missing goods)")
    @PatchMapping("/returns/{id}/discrepancy")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> reportDiscrepancy(
            @PathVariable UUID id,
            @Valid @RequestBody ReportReturnDiscrepancyRequest req,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                service.reportReturnDiscrepancy(id, id(tenant), id(user), role, req)));
    }

    // =========================================================================
    // 3. COD COLLECTIONS & REVIEW QUEUE
    // =========================================================================

    @PostMapping("/orders/{orderId}/cod-collections")
    public ResponseEntity<ApiResponse<?>> collect(
            @PathVariable UUID orderId,
            @Valid @RequestBody CollectCodRequest req,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(service.collect(orderId, id(tenant), id(user), role, req)));
    }

    @PatchMapping("/cod-collections/{id}/declare")
    public ResponseEntity<ApiResponse<?>> declare(
            @PathVariable UUID id,
            @Valid @RequestBody CodHandoverRequest req,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(service.declare(id, id(tenant), id(user), role, req)));
    }

    @GetMapping("/cod-collections/mine")
    public ResponseEntity<ApiResponse<?>> pendingHandover(
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(service.pendingHandover(id(tenant), id(user), role)));
    }

    @PatchMapping("/cod-collections/{id}/verify")
    public ResponseEntity<ApiResponse<?>> verify(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Id") String user,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(service.verify(id, id(tenant), id(user), role, body.get("notes"))));
    }

    @GetMapping("/review")
    public ResponseEntity<ApiResponse<OperationsReviewResponse>> review(
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(service.review(id(tenant), role)));
    }

    private UUID id(String value) {
        return UUID.fromString(value);
    }
}
