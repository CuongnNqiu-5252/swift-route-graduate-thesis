package com.swiftroute.orderservice.dto.request;

import com.swiftroute.orderservice.entity.OptimizationWeights;
import com.swiftroute.orderservice.entity.enums.BusinessType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request payload for a new tenant onboarding request. */
public record CreateTenantRequest(
        @NotBlank @Size(min = 2, max = 255) String businessName,
        @NotNull BusinessType businessType,
        @NotBlank @Size(min = 5, max = 500) String address,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double pickupLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double pickupLng,
        @NotNull @Valid OptimizationWeights optimizationWeights) {
}
