package com.swiftroute.orderservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cod_handover_batches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodHandoverBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "handover_id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "driver_id", nullable = false)
    private UUID driverId;
    @Column(name = "operation_id", nullable = false)
    private UUID operationId;
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "SUBMITTED";
    @Column(name = "ledger_total", nullable = false, precision = 12, scale = 0)
    private BigDecimal ledgerTotal;
    @Column(name = "declared_total", nullable = false, precision = 12, scale = 0)
    private BigDecimal declaredTotal;
    @Column(name = "counted_total", precision = 12, scale = 0)
    private BigDecimal countedTotal;
    @Column(name = "discrepancy_amount", precision = 12, scale = 0)
    private BigDecimal discrepancyAmount;
    private String notes;
    @Builder.Default
    private Instant submittedAt = Instant.now();
    private UUID verifiedBy;
    private Instant verifiedAt;
    private UUID verificationOperationId;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
    @Version
    private Long version;
}
