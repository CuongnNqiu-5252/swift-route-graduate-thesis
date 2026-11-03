package com.swiftroute.authservice.controller;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.authservice.dto.response.ApiResponse;
import com.swiftroute.authservice.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/email")
public class EmailVerificationController {
    private final EmailVerificationService verification;
    public record VerifyRequest(String token) {}
    @PostMapping("/verify") public ApiResponse<Void> verify(@RequestBody VerifyRequest request) {
        verification.verify(request.token()); return ApiResponse.success(null);
    }
    @PostMapping("/resend") public ApiResponse<Void> resend(Authentication auth) {
        verification.resend(UUID.fromString(auth.getName())); return ApiResponse.success(null);
    }
}
