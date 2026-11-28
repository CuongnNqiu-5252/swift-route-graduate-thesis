package com.swiftroute.orderservice.dto.response;

import java.util.UUID;

import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.BusinessType;
import com.swiftroute.orderservice.entity.enums.TenantStatus;

public record TenantSettingsResponse(
        UUID tenantId,
        String businessName,
        BusinessType businessType,
        TenantStatus status,
        TenantDispatchConfig dispatchConfig,
        OptimizationWeights optimizationWeights
) {
    public static TenantSettingsResponse fromEntity(Tenant tenant) {
        return new TenantSettingsResponse(
                tenant.getId(),
                tenant.getBusinessName(),
                tenant.getBusinessType(),
                tenant.getStatus(),
                tenant.getDispatchConfig() != null ? tenant.getDispatchConfig() : new TenantDispatchConfig(),
                tenant.getOptimizationWeights() != null ? tenant.getOptimizationWeights() : new OptimizationWeights()
        );
    }
}

