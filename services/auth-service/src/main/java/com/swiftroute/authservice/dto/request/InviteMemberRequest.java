package com.swiftroute.authservice.dto.request;

import com.swiftroute.authservice.entity.enums.TenantMembershipRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteMemberRequest(
        @NotBlank @Email String email,
        @NotNull TenantMembershipRole role,
        java.util.UUID driverId,
        java.util.List<String> grantedPermissions,
        java.util.List<String> revokedPermissions) {
    public InviteMemberRequest(String email, TenantMembershipRole role) {
        this(email, role, null, null, null);
    }

    public InviteMemberRequest(String email, TenantMembershipRole role, java.util.UUID driverId) {
        this(email, role, driverId, null, null);
    }
}

