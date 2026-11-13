package com.swiftroute.authservice.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.authservice.dto.response.ApiResponse;
import com.swiftroute.authservice.service.PasswordResetService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/password")
public class PasswordResetController {
    private final PasswordResetService service;

    public record ForgotRequest(@NotBlank @Email String email) {}
    public record ResetRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    @PostMapping("/forgot")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotRequest request) {
        service.requestReset(request.email());
        return ApiResponse.success(null);
    }

    @PostMapping("/reset")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetRequest request) {
        service.resetPassword(request.token(), request.newPassword());
        return ApiResponse.success(null);
    }
}
