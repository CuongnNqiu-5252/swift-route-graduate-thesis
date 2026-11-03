package com.swiftroute.authservice.dto.request;

import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberStatusRequest(
        @NotNull TenantMembershipStatus status) {
}

