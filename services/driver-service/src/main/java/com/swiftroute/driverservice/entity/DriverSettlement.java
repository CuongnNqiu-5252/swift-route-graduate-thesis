package com.swiftroute.driverservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.swiftroute.driverservice.entity.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "driver_settlement")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DriverSettlement {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_id", nullable = false, updatable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "driver_id", nullable = false) private UUID driverId;
    @Column(name = "period_start", nullable = false) private Instant periodStart;
    @Column(name = "period_end", nullable = false) private Instant periodEnd;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20)
    @Builder.Default private SettlementStatus status = SettlementStatus.DRAFT;
    @Column(name = "earning_count", nullable = false) private Integer earningCount;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0) private BigDecimal totalAmount;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "paid_by") private UUID paidBy;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "payment_reference", length = 120) private String paymentReference;
    @Column(name = "created_at", nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) @Builder.Default private Instant updatedAt = Instant.now();
}
