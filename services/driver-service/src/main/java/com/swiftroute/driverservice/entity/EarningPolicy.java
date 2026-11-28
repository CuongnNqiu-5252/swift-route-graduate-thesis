package com.swiftroute.driverservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "earning_policy")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EarningPolicy {
    @Id @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "base_amount", nullable = false, precision = 12, scale = 0) private BigDecimal baseAmount;
    @Column(name = "per_km_amount", nullable = false, precision = 12, scale = 0) private BigDecimal perKmAmount;
    @Column(name = "minimum_amount", nullable = false, precision = 12, scale = 0) private BigDecimal minimumAmount;
    @Column(name = "heavy_weight_threshold_kg", nullable = false, precision = 8, scale = 2) private BigDecimal heavyWeightThresholdKg;
    @Column(name = "heavy_bonus_amount", nullable = false, precision = 12, scale = 0) private BigDecimal heavyBonusAmount;
    @Column(name = "currency", nullable = false, length = 3) @Builder.Default private String currency = "VND";
    @Column(name = "created_at", nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) @Builder.Default private Instant updatedAt = Instant.now();
}
