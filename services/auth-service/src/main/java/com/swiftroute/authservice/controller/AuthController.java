package com.swiftroute.authservice.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.authservice.dto.request.LoginRequestDTO;
import com.swiftroute.authservice.dto.request.RegisterRequestDTO;
import com.swiftroute.authservice.dto.request.UpdateProfileRequestDTO;
import com.swiftroute.authservice.dto.response.ApiResponse;
import com.swiftroute.authservice.dto.response.LoginResponseDTO;
import com.swiftroute.authservice.dto.response.UserProfileResponseDTO;
import com.swiftroute.authservice.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final com.swiftroute.authservice.service.TenantEmployeeService tenantEmployeeService;

    public AuthController(AuthService authService, com.swiftroute.authservice.service.TenantEmployeeService tenantEmployeeService) {
        this.authService = authService;
        this.tenantEmployeeService = tenantEmployeeService;
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<ApiResponse<com.swiftroute.authservice.dto.response.TenantMemberResponse>> acceptInvitation(
            @Valid @RequestBody com.swiftroute.authservice.dto.request.AcceptInvitationRequest request) {
        com.swiftroute.authservice.dto.response.TenantMemberResponse response = tenantEmployeeService.acceptInvitation(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    public record PreviewInvitationRequest(@jakarta.validation.constraints.NotBlank String token) {}

    @PostMapping("/invitations/preview")
    public ResponseEntity<ApiResponse<com.swiftroute.authservice.service.TenantEmployeeService.InvitationPreview>> previewInvitation(
            @Valid @RequestBody PreviewInvitationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tenantEmployeeService.previewInvitation(request.token())));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequestDTO request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest) {
        LoginResponseDTO response = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> refresh(
            @RequestParam("refreshToken") String refreshToken, HttpServletRequest request) {
        LoginResponseDTO response = authService.refreshAccessToken(refreshToken, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestParam("refreshToken") String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> getProfile(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        UserProfileResponseDTO profile = authService.getProfile(userId, effectiveRole());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> updateProfile(
            @Valid @RequestBody UpdateProfileRequestDTO request) {
        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        return ResponseEntity.ok(ApiResponse.success(authService.updateProfile(userId, request, effectiveRole())));
    }

    private String effectiveRole() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .filter(value -> value.startsWith("ROLE_"))
                .map(value -> value.substring(5))
                .findFirst()
                .orElse(null);
    }
}
