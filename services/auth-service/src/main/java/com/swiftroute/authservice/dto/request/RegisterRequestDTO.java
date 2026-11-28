package com.swiftroute.authservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequestDTO {
    @Email(message = "Email invalid")
    private String email;
    @NotBlank(message = "Password required")
    @Size(min = 6)
    private String password;
    @NotBlank(message = "Full name required")
    private String fullName;
    @NotBlank
    private String phoneNumber;
}
