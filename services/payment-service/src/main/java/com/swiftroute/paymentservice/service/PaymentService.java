package com.swiftroute.paymentservice.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.swiftroute.paymentservice.dto.event.PaymentCompletedEvent;
import com.swiftroute.paymentservice.dto.request.CreatePaymentRequest;
import com.swiftroute.paymentservice.dto.response.PaymentResponse;
import com.swiftroute.paymentservice.entity.Payment;
import com.swiftroute.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request, UUID customerId, UUID tenantId) {
        Payment payment = new Payment();
        payment.setOrderId(request.orderId()); payment.setCustomerId(customerId); payment.setTenantId(tenantId);
        payment.setAmount(request.amount()); payment.setGateway(normalize(request.gateway(), "MOCK"));
        payment.setMode(normalize(request.mode(), "MOCK")); payment.setStatus("PENDING");
        payment.setTxnRef("SR" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase());
        payment.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse confirmMock(UUID paymentId, UUID customerId, String role) {
        Payment payment = getPayment(paymentId);
        boolean privileged = "ADMIN".equals(role) || "SYSTEM_ADMIN".equals(role) || "OPS_STAFF".equals(role);
        if (!privileged && !payment.getCustomerId().equals(customerId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment not owned by caller");
        if ("SUCCESS".equals(payment.getStatus())) return PaymentResponse.from(payment);
        if (!"PENDING".equals(payment.getStatus()) || payment.getExpiresAt().isBefore(Instant.now())) {
            if (payment.getExpiresAt().isBefore(Instant.now())) payment.setStatus("EXPIRED");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment cannot be confirmed");
        }
        if (!"MOCK".equals(payment.getGateway())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only MOCK payments may be confirmed here");
        payment.setStatus("SUCCESS"); payment.setPaidAt(Instant.now()); payment.setGatewayTxnId(payment.getTxnRef());
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publishCompleted(new PaymentCompletedEvent(saved.getOrderId(), saved.getId(), saved.getAmount(), saved.getTxnRef()));
        return PaymentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId, UUID customerId, String role) {
        Payment payment = getPayment(paymentId);
        boolean privileged = "ADMIN".equals(role) || "SYSTEM_ADMIN".equals(role) || "OPS_STAFF".equals(role);
        if (!privileged && !payment.getCustomerId().equals(customerId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment not owned by caller");
        return PaymentResponse.from(payment);
    }

    private Payment getPayment(UUID paymentId) { return paymentRepository.findById(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found")); }
    private String normalize(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.toUpperCase(); }
}
