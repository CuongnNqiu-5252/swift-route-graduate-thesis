package com.swiftroute.authservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @Size(min = 6, max = 100) String password,
        @JsonProperty("full_name") @JsonAlias({"fullName", "full_name"}) @Size(max = 255) String fullName) {
}

