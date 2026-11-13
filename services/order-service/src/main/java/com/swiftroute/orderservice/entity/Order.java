package com.swiftroute.orderservice.entity;

import java.math.BigDecimal;
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

    /** Mã vận đơn riêng từ hệ thống của Shop / POS / ERP đối tác */
    @Column(name = "external_order_code", length = 100)
    private String externalOrderCode;

    /** Mã token ngẫu nhiên URL-safe phục vụ tra cứu công khai không cần tài khoản */
    @Column(name = "tracking_token", nullable = false, unique = true, length = 64)
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

    /** Human-readable description of the physical package */
    @Column(name = "package_description", length = 500)
    private String packageDescription;

    @Column(name = "package_count", nullable = false)
    @Builder.Default
    private Integer packageCount = 1;

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
    private OrderStatus status = OrderStatus.PAID;

    @Column(name = "fulfillment_status", length = 30)
    @Builder.Default
    private String fulfillmentStatus = "READY_FOR_PLANNING";

    @Column(name = "payment_status", length = 30)
    @Builder.Default
    private String paymentStatus = "COD_PENDING";

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

    /** Giá trị hàng khai báo phục vụ bảo hiểm/đền bù */
    @Column(name = "declared_value", nullable = false, precision = 12, scale = 0)
    @Builder.Default
    private BigDecimal declaredValue = BigDecimal.ZERO;

    /** Cước phí vận chuyển */
    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 0)
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    /** Bên thanh toán cước: SENDER hoặc RECIPIENT */
    @Column(name = "payer", nullable = false, length = 20)
    @Builder.Default
    private String payer = "SENDER";

    /** Tổng số tiền cần thu từ người nhận (COD + cước phí nếu người nhận trả cước) */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

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
