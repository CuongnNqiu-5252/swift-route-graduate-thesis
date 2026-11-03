package com.swiftroute.orderservice.dto.request;

import com.swiftroute.orderservice.entity.enums.BusinessType;

import jakarta.validation.constraints.NotNull;

public record ApplyPresetRequest(
        @NotNull(message = "Loại hình kinh doanh không được để trống")
        BusinessType businessType
) {}

