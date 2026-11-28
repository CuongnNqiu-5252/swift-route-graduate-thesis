package com.swiftroute.authservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank @JsonAlias("idToken") String idToken) {
}
