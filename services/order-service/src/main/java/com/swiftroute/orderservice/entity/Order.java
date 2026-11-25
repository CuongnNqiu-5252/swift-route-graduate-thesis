package com.swiftroute.orderservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
// Name the tracking_token unique constraint explicitly so Hibernate matches Flyway's
// ux_orders_tracking_token instead of generating unnamed uk* duplicate constraints.
@Table(
    name = "orders",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_orders_tracking_token",
        columnNames = "tracking_token"
    )
)
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

    @Version
    private Long version;

    // Khoá ngoại vật lý tới bảng tenants (Bắt buộc)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Mã vận đơn riêng từ hệ thống của Shop / POS / ERP đối tác */
    @Column(name = "external_order_code", length = 100)
    private String externalOrderCode;

    /** Mã token ngẫu nhiên URL-safe phục vụ tra cứu công khai không cần tài khoản */
    @Column(name = "tracking_token", nullable = false, length = 64)
    private String trackingToken;

    /** Thông tin người gửi tại điểm lấy hàng */
    @Column(name = "sender_name", length = 100)
    private String senderName;

    @Column(name = "sender_phone", length = 50)
    private String senderPhone;

    /** Thông tin người nhận hàng */
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 50)
    private String recipientPhone;

    @Column(name = "pickup_lat", nullable = false)
    private Double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private Double pickupLng;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_location_id")
    private PickupLocation pickupLocation;

    @Column(name = "pickup_address", length = 500)
    private String pickupAddress;

    @Column(name = "delivery_lat", nullable = false)
    private Double deliveryLat;

    @Column(name = "delivery_lng", nullable = false)
    private Double deliveryLng;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    /** UTC timestamp — start of the allowed delivery window */
    @Column(name = "time_window_start", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime timeWindowStart;

    /** UTC timestamp — end of the allowed delivery window */
    @Column(name = "time_window_end", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime timeWindowEnd;

    @Column(name = "weight_kg", nullable = false)
    private Double weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_category", nullable = false, length = 30)
    @Builder.Default
    private PackageCategory packageCategory = PackageCategory.PARCEL;

    /** Human-readable description of the physical package */
    @Column(name = "package_description", length = 500)
    private String packageDescription;

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
    private OrderStatus status = OrderStatus.READY_FOR_DISPATCH;

    @Column(name = "cod_status", length = 30)
    @Builder.Default
    private String codStatus = "NOT_APPLICABLE";

    @Column(name = "cod_collected_at")
    private Instant codCollectedAt;

    @Column(name = "cod_handover_batch_id")
    private UUID codHandoverBatchId;

    /** Tiền thu hộ COD tài xế thu khi giao hàng */
    @Column(name = "cod_amount", nullable = false, precision = 12, scale = 0)
    @Builder.Default
    private BigDecimal codAmount = BigDecimal.ZERO;

    /** Tổng số tiền cần thu từ người nhận (COD) */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // Khoá ngoại logic tới driver-service
    @Column(name = "assigned_driver_id")
    private UUID assignedDriverId;

    /** Driver currently holding physical custody of the package (null when at depot or released) */
    @Column(name = "custodian_driver_id")
    private UUID custodianDriverId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    @JsonIgnore
    @Setter(AccessLevel.PACKAGE)
    private DispatchPlan dispatchPlan;

    @Column(name = "plan_stop_index")
    private Integer planStopIndex;

    public UUID getPlanId() {
        return dispatchPlan == null ? null : dispatchPlan.getId();
    }

    public boolean canJoinDispatchPlan() {
        return status == OrderStatus.READY_FOR_DISPATCH && dispatchPlan == null;
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
