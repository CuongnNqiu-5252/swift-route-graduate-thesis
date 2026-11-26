package com.swiftroute.driverservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record SendActivationEmailRequest(
        @Email(message = "email format is invalid")
        @Size(max = 255)
        String email) {
}
