package com.swiftroute.orderservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.swiftroute.orderservice.entity.enums.BusinessType;
import com.swiftroute.orderservice.entity.enums.SubscriptionPlan;
import com.swiftroute.orderservice.entity.enums.TenantStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 50)
    @Builder.Default
    private BusinessType businessType = BusinessType.OTHER;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "pickup_lat")
    private Double pickupLat;

    @Column(name = "pickup_lng")
    private Double pickupLng;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "optimization_weights", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private OptimizationWeights optimizationWeights = new OptimizationWeights(0.5, 0.5);

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false, length = 50)
    @Builder.Default
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE_TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TenantStatus status = TenantStatus.PENDING;

    @Column(name = "approval_reason", length = 500)
    private String approvalReason;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "status_updated_at", nullable = false)
    private LocalDateTime statusUpdatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
