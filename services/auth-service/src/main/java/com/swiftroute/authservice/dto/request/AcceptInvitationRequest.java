package com.swiftroute.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @Size(min = 6, max = 100) String password,
        @Size(max = 255) String fullName) {
}

