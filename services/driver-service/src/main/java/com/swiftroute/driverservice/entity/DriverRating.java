package com.swiftroute.driverservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "driver_rating")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "driver_rating_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    /** Logical reference to order_service.orders — no physical FK */
    @Column(name = "order_id")
    private UUID orderId;

    /** Logical reference to the customer who rated — no physical FK */
    @Column(name = "customer_id")
    private UUID customerId;

    /** Rating score from 1 (worst) to 5 (best) */
    @Min(1)
    @Max(5)
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "review", length = 500)
    private String review;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
