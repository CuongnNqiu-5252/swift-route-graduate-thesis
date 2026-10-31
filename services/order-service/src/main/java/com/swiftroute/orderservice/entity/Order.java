package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PackageCategory;

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

    @Column(name = "pickup_address", length = 500)
    private String pickupAddress;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    /** UTC timestamp — start of the allowed delivery window */
    @Column(name = "time_window_start", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime timeWindowStart;

    /** UTC timestamp — end of the allowed delivery window */
    @Column(name = "time_window_end", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime timeWindowEnd;

    @Column(name = "weight_kg", nullable = false)
    private Double weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_category", nullable = false, length = 30)
    @Builder.Default
    private PackageCategory packageCategory = PackageCategory.PARCEL;

    /** Human-readable description of the items being delivered */
    @Column(name = "item_description", length = 255)
    private String itemDescription;

    /** Special instructions for the delivery driver */
    @Column(name = "notes", length = 500)
    private String notes;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

}
