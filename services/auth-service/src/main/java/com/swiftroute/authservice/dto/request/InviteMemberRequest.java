package com.swiftroute.authservice.dto.request;

import com.swiftroute.authservice.entity.enums.TenantMembershipRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteMemberRequest(
        @NotBlank @Email String email,
        @NotNull TenantMembershipRole role) {
}

