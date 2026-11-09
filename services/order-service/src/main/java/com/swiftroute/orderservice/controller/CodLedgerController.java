package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ledger.recordCollection(orderId, tenantId, driverId, role, request)));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<CodPaymentSummaryResponse>> orderSummary(
            @PathVariable UUID orderId,
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(ledger.orderSummary(orderId, tenantId)));
    }

    @GetMapping("/mine/custody")
    public ResponseEntity<ApiResponse<DriverCodCustodyResponse>> myCustody(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(ledger.driverCustody(tenantId, driverId, role)));
    }

    @PostMapping("/handovers")
    public ResponseEntity<ApiResponse<CodHandoverResponse>> submitHandover(
            @Valid @RequestBody SubmitCodHandoverRequest request,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ledger.submitHandover(tenantId, driverId, role, request)));
    }

    @GetMapping("/handovers/mine")
    public ResponseEntity<ApiResponse<List<CodHandoverResponse>>> myHandovers(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(ledger.driverHandovers(tenantId, driverId, role)));
    }

    @GetMapping("/handovers")
    public ResponseEntity<ApiResponse<List<CodHandoverResponse>>> handovers(
            @RequestParam(required = false) String status,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(ledger.handoverQueue(tenantId, role, status)));
    }

    @PostMapping("/handovers/{handoverId}/verify")
    public ResponseEntity<ApiResponse<CodHandoverResponse>> verifyHandover(
            @PathVariable UUID handoverId,
            @Valid @RequestBody VerifyCodHandoverRequest request,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID cashierId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(
                ledger.verifyHandover(handoverId, tenantId, cashierId, role, request)));
    }

    @GetMapping("/disputes")
    public ResponseEntity<ApiResponse<List<CodDisputeResponse>>> disputes(
            @RequestParam(required = false) String status,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(List.of()));
    }

    @PostMapping("/disputes/{disputeId}/resolve")
    public ResponseEntity<ApiResponse<Map<String, String>>> resolveDispute(
            @PathVariable UUID disputeId,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID actorId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "RESOLVED")));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<CodDashboardResponse>> dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(ledger.dashboard(tenantId, role)));
    }
}
