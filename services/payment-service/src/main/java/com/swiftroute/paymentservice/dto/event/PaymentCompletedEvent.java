package com.swiftroute.paymentservice.dto.event;

import java.math.BigDecimal;
import java.util.UUID;
public record PaymentCompletedEvent(UUID orderId, UUID paymentId, BigDecimal amount, String txnRef) { }
