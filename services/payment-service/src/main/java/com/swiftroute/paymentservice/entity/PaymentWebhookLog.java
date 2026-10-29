package com.swiftroute.paymentservice.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_webhook_logs")
@Getter @Setter @NoArgsConstructor
public class PaymentWebhookLog {
    @Id @GeneratedValue @UuidGenerator @Column(name = "log_id") private UUID id;
    @Column(name = "payment_id") private UUID paymentId;
    @Column(nullable = false) private String gateway;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb") private Object rawPayload;
    @Column(name = "signature_valid", nullable = false) private boolean signatureValid;
    @CreationTimestamp @Column(name = "received_at", nullable = false, updatable = false) private Instant receivedAt;
}
