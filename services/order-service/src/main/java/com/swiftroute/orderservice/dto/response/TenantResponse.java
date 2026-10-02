package com.swiftroute.orderservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import com.swiftroute.orderservice.entity.enums.SubscriptionPlan;
import com.swiftroute.orderservice.entity.enums.TenantStatus;

/** Public tenant representation; never exposes the persistence entity directly. */
public record TenantResponse(
        UUID tenantId,
        UUID ownerUserId,
        String businessName,
        BusinessType businessType,
        String address,
        Double pickupLat,
        Double pickupLng,
        TenantStatus status,
        SubscriptionPlan subscriptionPlan,
        String approvalReason,
        LocalDateTime createdAt,
        LocalDateTime statusUpdatedAt) {

    public static TenantResponse fromEntity(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(), tenant.getOwnerUserId(), tenant.getBusinessName(), tenant.getBusinessType(),
                tenant.getAddress(), tenant.getPickupLat(), tenant.getPickupLng(), tenant.getStatus(),
                tenant.getSubscriptionPlan(), tenant.getApprovalReason(), tenant.getCreatedAt(),
                tenant.getStatusUpdatedAt());
    }
}
