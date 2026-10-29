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

    /** Logical reference to order_service.orders — no physical FK */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    /** Total amount paid by customer for the order */
    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    /** Platform commission deducted from gross amount */
    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee;

    /** Net amount received by the driver (gross_amount - platform_fee) */
    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    /** Payment/settlement status of this earning record */
    @Column(name = "payment_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EarningStatus paymentStatus = EarningStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
