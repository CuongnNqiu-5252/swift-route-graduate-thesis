package com.swiftroute.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequestDTO(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Size(min = 9, max = 20) String phoneNumber) {}
