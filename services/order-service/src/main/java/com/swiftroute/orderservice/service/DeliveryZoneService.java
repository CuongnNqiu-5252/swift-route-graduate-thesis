package com.swiftroute.orderservice.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.common.GeoDistanceUtils;
import com.swiftroute.orderservice.dto.request.UpsertDeliveryZoneRequest;
import com.swiftroute.orderservice.dto.response.DeliveryZoneCheckResponse;
import com.swiftroute.orderservice.dto.response.DeliveryZoneResponse;
import com.swiftroute.orderservice.entity.AuditLog;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDeliveryZone;
import com.swiftroute.orderservice.entity.enums.DeliveryZoneEnforcementMode;
import com.swiftroute.orderservice.entity.enums.DeliveryZoneType;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.AuditLogRepository;
import com.swiftroute.orderservice.repository.TenantDeliveryZoneRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages tenant delivery zone boundaries and validates delivery drop-off coordinates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryZoneService {

    private final TenantDeliveryZoneRepository deliveryZoneRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public DeliveryZoneResponse getZone(Tenant tenant) {
        return deliveryZoneRepository.findByTenantId(tenant.getId())
                .map(DeliveryZoneResponse::fromEntity)
                .orElseGet(() -> DeliveryZoneResponse.unconfigured(tenant.getId()));
    }

    @Transactional
    public DeliveryZoneResponse upsertZone(Tenant tenant, UpsertDeliveryZoneRequest request, UUID actorId) {
        TenantDeliveryZone zone = deliveryZoneRepository.findByTenantId(tenant.getId())
                .orElseGet(() -> TenantDeliveryZone.builder()
                        .tenant(tenant)
                        .zoneType(DeliveryZoneType.CIRCLE)
                        .build());

        zone.setZoneType(DeliveryZoneType.CIRCLE);
        zone.setCenterLat(request.centerLat());
        zone.setCenterLng(request.centerLng());
        zone.setRadiusKm(request.radiusKm());
        zone.setEnforcementMode(request.enforcementMode());
        zone.setIsActive(request.isActive());

        TenantDeliveryZone saved = deliveryZoneRepository.saveAndFlush(zone);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("zone_type", saved.getZoneType().name());
        auditDetails.put("center_lat", saved.getCenterLat());
        auditDetails.put("center_lng", saved.getCenterLng());
        auditDetails.put("radius_km", saved.getRadiusKm());
        auditDetails.put("enforcement_mode", saved.getEnforcementMode().name());
        auditDetails.put("is_active", saved.getIsActive());

        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenant.getId())
                .aggregateType("TENANT_DELIVERY_ZONE")
                .aggregateId(saved.getId())
                .action("DELIVERY_ZONE_UPDATED")
                .actorId(actorId != null ? actorId : tenant.getOwnerUserId())
                .details(auditDetails)
                .build());

        return DeliveryZoneResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public DeliveryZoneCheckResponse checkLocation(UUID tenantId, double lat, double lng) {
        var zoneOpt = deliveryZoneRepository.findByTenantId(tenantId);
        if (zoneOpt.isEmpty()) {
            return new DeliveryZoneCheckResponse(
                    false,
                    true,
                    true,
                    0.0,
                    null,
                    null,
                    "Doanh nghiệp chưa thiết lập vùng giao hàng."
            );
        }

        TenantDeliveryZone zone = zoneOpt.get();
        double rawDistanceKm = GeoDistanceUtils.haversineKm(zone.getCenterLat(), zone.getCenterLng(), lat, lng);
        double distanceKm = Math.round(rawDistanceKm * 100.0) / 100.0;
        boolean inZone = rawDistanceKm <= zone.getRadiusKm();

        if (!Boolean.TRUE.equals(zone.getIsActive())) {
            return new DeliveryZoneCheckResponse(
                    true,
                    true,
                    inZone,
                    distanceKm,
                    zone.getRadiusKm(),
                    zone.getEnforcementMode(),
                    String.format("Cách tâm %.2f km (giới hạn vùng giao hàng đang tạm tắt).", distanceKm)
            );
        }

        boolean allowed = inZone || zone.getEnforcementMode() == DeliveryZoneEnforcementMode.WARN_ONLY;
        String message;
        if (inZone) {
            message = String.format("Trong phạm vi phục vụ (cách tâm %.2f km / %.1f km).", distanceKm, zone.getRadiusKm());
        } else if (zone.getEnforcementMode() == DeliveryZoneEnforcementMode.WARN_ONLY) {
            message = String.format("Ngoài bán kính %.1f km (%.2f km) — chế độ chỉ cảnh báo vẫn cho tạo đơn.", zone.getRadiusKm(), distanceKm);
        } else {
            message = String.format("Ngoài bán kính phục vụ %.1f km (cách tâm %.2f km) — đơn hàng sẽ bị chặn.", zone.getRadiusKm(), distanceKm);
        }

        return new DeliveryZoneCheckResponse(
                true,
                allowed,
                inZone,
                distanceKm,
                zone.getRadiusKm(),
                zone.getEnforcementMode(),
                message
        );
    }

    /**
     * Enforces an active tenant delivery zone before creating an order.
     */
    @Transactional(readOnly = true)
    public void validateDeliveryLocation(UUID tenantId, double deliveryLat, double deliveryLng) {
        var zoneOpt = deliveryZoneRepository.findByTenantIdAndIsActiveTrue(tenantId);
        if (zoneOpt.isEmpty()) {
            return;
        }

        TenantDeliveryZone zone = zoneOpt.get();
        double distanceKm = GeoDistanceUtils.haversineKm(zone.getCenterLat(), zone.getCenterLng(), deliveryLat, deliveryLng);
        if (distanceKm <= zone.getRadiusKm()) {
            return;
        }

        if (zone.getEnforcementMode() == DeliveryZoneEnforcementMode.STRICT_BLOCK) {
            throw new AppException(ErrorCode.DELIVERY_OUT_OF_ZONE);
        }

        log.warn("Order delivery point outside zone (WARN_ONLY): tenantId={}, distanceKm={}, radiusKm={}",
                tenantId, Math.round(distanceKm * 100.0) / 100.0, zone.getRadiusKm());
    }
}
