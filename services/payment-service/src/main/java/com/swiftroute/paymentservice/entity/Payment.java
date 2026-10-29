package com.swiftroute.paymentservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue @UuidGenerator
    @Column(name = "payment_id", updatable = false, nullable = false)
    private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency = "VND";
    @Column(nullable = false, length = 20) private String gateway;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false, length = 20) private String mode;
    @Column(name = "txn_ref", nullable = false, unique = true, length = 64) private String txnRef;
    @Column(name = "gateway_txn_id", length = 64) private String gatewayTxnId;
    @Column(name = "payment_url", columnDefinition = "text") private String paymentUrl;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @CreationTimestamp @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    @Version @Column(nullable = false) private Long version;
}
