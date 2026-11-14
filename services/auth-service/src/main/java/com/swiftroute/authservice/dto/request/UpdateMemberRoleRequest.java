package com.swiftroute.authservice.dto.request;

import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull(message = "Role is required")
        TenantMembershipRole role
) {
}
