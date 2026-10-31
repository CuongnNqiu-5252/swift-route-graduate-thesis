package com.swiftroute.orderservice.dto.request;

import com.swiftroute.orderservice.entity.enums.TenantStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request payload for a platform administrator's tenant state decision. */
public record TenantStatusRequest(
        @NotNull TenantStatus status,
        @Size(max = 500) String reason) {
}
