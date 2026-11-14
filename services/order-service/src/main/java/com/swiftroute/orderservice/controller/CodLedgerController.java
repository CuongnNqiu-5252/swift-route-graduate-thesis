package com.swiftroute.orderservice.controller;

import com.swiftroute.orderservice.dto.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.RecordCodCollectionRequest;
import com.swiftroute.orderservice.dto.request.SubmitCodHandoverRequest;
import com.swiftroute.orderservice.dto.request.VerifyCodHandoverRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.CodCollectionResponse;
import com.swiftroute.orderservice.dto.response.CodDashboardResponse;
import com.swiftroute.orderservice.dto.response.CodDisputeResponse;
import com.swiftroute.orderservice.dto.response.CodHandoverResponse;
import com.swiftroute.orderservice.dto.response.CodPaymentSummaryResponse;
import com.swiftroute.orderservice.dto.response.DriverCodCustodyResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.service.CodLedgerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cod")
@RequiredArgsConstructor
public class CodLedgerController {

    private final CodLedgerService ledger;

    @PostMapping("/orders/{orderId}/collections")
    public ResponseEntity<ApiResponse<CodCollectionResponse>> recordCollection(
            @PathVariable UUID orderId,
            @Valid @RequestBody RecordCodCollectionRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID driverId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ledger.recordCollection(orderId, requireTenantId(tenantId), driverId, role, request)));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<CodPaymentSummaryResponse>> orderSummary(
            @PathVariable UUID orderId,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(ledger.orderSummary(orderId, requireTenantId(tenantId))));
    }

    @GetMapping("/mine/custody")
    public ResponseEntity<ApiResponse<DriverCodCustodyResponse>> myCustody(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID driverId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(ledger.driverCustody(requireTenantId(tenantId), driverId, role)));
    }

    @PostMapping("/handovers")
    public ResponseEntity<ApiResponse<CodHandoverResponse>> submitHandover(
            @Valid @RequestBody SubmitCodHandoverRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID driverId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ledger.submitHandover(requireTenantId(tenantId), driverId, role, request)));
    }

    @GetMapping("/handovers/page")
    public ApiResponse<PageResponse<CodHandoverResponse>> handoverPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("effectiveRole") String role) {
        return ApiResponse.success(ledger.handoverPage(requireTenantId(tenantId), role, status, page, size));
    }

    @GetMapping("/handovers")
    public ResponseEntity<ApiResponse<List<CodHandoverResponse>>> handovers(
            @RequestParam(required = false) String status,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(ledger.handoverQueue(requireTenantId(tenantId), role, status)));
    }

    @PostMapping("/handovers/{handoverId}/verify")
    public ResponseEntity<ApiResponse<CodHandoverResponse>> verifyHandover(
            @PathVariable UUID handoverId,
            @Valid @RequestBody VerifyCodHandoverRequest request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID cashierId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                ledger.verifyHandover(handoverId, requireTenantId(tenantId), cashierId, role, request)));
    }

    @GetMapping("/disputes/page")
    public ApiResponse<PageResponse<CodDisputeResponse>> disputePage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("effectiveRole") String role) {
        return ApiResponse.success(ledger.disputePage(requireTenantId(tenantId), role, status, page, size));
    }

    @GetMapping("/disputes")
    public ResponseEntity<ApiResponse<List<CodDisputeResponse>>> disputes(
            @RequestParam(required = false) String status,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(ledger.disputes(requireTenantId(tenantId), role, status)));
    }

    @PostMapping("/disputes/{disputeId}/resolve")
    public ResponseEntity<ApiResponse<CodHandoverResponse>> resolveDispute(
            @PathVariable UUID disputeId,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("authorizedUserId") UUID actorId,
            @RequestAttribute("effectiveRole") String role) {
        String notes = null;
        if (request != null) {
            Object raw = request.get("resolution_notes");
            if (raw == null) raw = request.get("notes");
            if (raw == null) raw = request.get("reason");
            notes = raw != null ? raw.toString() : null;
        }
        return ResponseEntity.ok(ApiResponse.success(
                ledger.resolveDispute(disputeId, requireTenantId(tenantId), actorId, role, notes)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<CodDashboardResponse>> dashboard(
            @RequestAttribute(name = "authorizedTenantId", required = false) UUID tenantId,
            @RequestAttribute("effectiveRole") String role) {
        return ResponseEntity.ok(ApiResponse.success(ledger.dashboard(requireTenantId(tenantId), role)));
    }

    private UUID requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return tenantId;
    }
}
