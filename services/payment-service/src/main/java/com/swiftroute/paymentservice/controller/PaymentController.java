package com.swiftroute.paymentservice.controller;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.swiftroute.paymentservice.dto.request.CreatePaymentRequest;
import com.swiftroute.paymentservice.dto.response.ApiResponse;
import com.swiftroute.paymentservice.dto.response.PaymentResponse;
import com.swiftroute.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(@Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("X-User-Id") String userId, @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        UUID parsedTenant = tenantId == null || tenantId.isBlank() ? null : UUID.fromString(tenantId);
        return ResponseEntity.ok(ApiResponse.success(paymentService.create(request, UUID.fromString(userId), parsedTenant)));
    }
    @PostMapping("/{paymentId}/confirm-mock")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(@PathVariable UUID paymentId, @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.confirmMock(paymentId, UUID.fromString(userId), role)));
    }
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(@PathVariable UUID paymentId, @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.get(paymentId, UUID.fromString(userId), role)));
    }
}
