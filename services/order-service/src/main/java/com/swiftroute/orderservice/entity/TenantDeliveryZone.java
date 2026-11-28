package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.orderservice.entity.enums.DeliveryZoneEnforcementMode;
import com.swiftroute.orderservice.entity.enums.DeliveryZoneType;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant_delivery_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantDeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "zone_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 30)
    @Builder.Default
    private DeliveryZoneType zoneType = DeliveryZoneType.CIRCLE;

    @Column(name = "center_lat", nullable = false)
    private Double centerLat;

    @Column(name = "center_lng", nullable = false)
    private Double centerLng;

    @Column(name = "radius_km", nullable = false)
    private Double radiusKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "enforcement_mode", nullable = false, length = 20)
    @Builder.Default
    private DeliveryZoneEnforcementMode enforcementMode = DeliveryZoneEnforcementMode.STRICT_BLOCK;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (zoneType == null) {
            zoneType = DeliveryZoneType.CIRCLE;
        }
        if (enforcementMode == null) {
            enforcementMode = DeliveryZoneEnforcementMode.STRICT_BLOCK;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
