package com.swiftroute.orderservice.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "order_id", updatable = false, nullable = false)
    private UUID id;

    // Khoá ngoại vật lý tới bảng tenants (Bắt buộc)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // Khoá ngoại logic tới auth-service (chỉ lưu UUID)
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "pickup_lat", nullable = false)
    private Double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private Double pickupLng;

    @Column(name = "delivery_lat", nullable = false)
    private Double deliveryLat;

    @Column(name = "delivery_lng", nullable = false)
    private Double deliveryLng;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @Column(name = "time_window_start", nullable = false)
    private LocalTime timeWindowStart;

    @Column(name = "time_window_end", nullable = false)
    private LocalTime timeWindowEnd;

    @Column(name = "weight_kg", nullable = false)
    private Double weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private OrderPriority priority = OrderPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    // Khoá ngoại logic tới driver-service
    @Column(name = "assigned_driver_id")
    private UUID assignedDriverId;

    // Khoá ngoại logic tới routing-engine-service
    @Column(name = "plan_id")
    private UUID planId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
