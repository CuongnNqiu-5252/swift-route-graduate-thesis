package com.swiftroute.driverservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftroute.driverservice.entity.enums.EarningStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "driver_earning")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverEarning {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "driver_earning_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "delivery_task_id", unique = true)
    private UUID deliveryTaskId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "earning_type", nullable = false, length = 20)
    @Builder.Default
    private String earningType = "DELIVERY";

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 0)
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "payable_distance_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal payableDistanceKm;

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal baseAmount;

    @Column(name = "distance_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal distanceAmount;

    @Column(name = "bonus_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal bonusAmount;

    @Column(name = "adjustment_amount", nullable = false, precision = 12, scale = 0)
    @Builder.Default
    private BigDecimal adjustmentAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal netAmount;

    @Column(name = "calculation_snapshot", nullable = false, columnDefinition = "TEXT")
    private String calculationSnapshot;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EarningStatus status = EarningStatus.PENDING;

    @Column(name = "settlement_id")
    private UUID settlementId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
