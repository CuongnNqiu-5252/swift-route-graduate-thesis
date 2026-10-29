package com.swiftroute.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.swiftroute.paymentservice.entity.Payment;

public record PaymentResponse(UUID paymentId, UUID orderId, BigDecimal amount, String currency,
        String gateway, String status, String mode, String txnRef, String paymentUrl,
        Instant paidAt, Instant expiresAt, Instant createdAt) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getCurrency(),
                payment.getGateway(), payment.getStatus(), payment.getMode(), payment.getTxnRef(),
                payment.getPaymentUrl(), payment.getPaidAt(), payment.getExpiresAt(), payment.getCreatedAt());
    }
}
