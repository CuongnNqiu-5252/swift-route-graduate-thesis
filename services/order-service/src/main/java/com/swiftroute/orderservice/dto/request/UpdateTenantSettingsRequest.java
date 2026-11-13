package com.swiftroute.orderservice.dto.request;

import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.BusinessType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateTenantSettingsRequest(
        @Size(max = 255)
        String businessName,

        BusinessType businessType,

        @Size(max = 500)
        String address,

        @Valid
        TenantDispatchConfig dispatchConfig,

        @Valid
        OptimizationWeights optimizationWeights
) {}

