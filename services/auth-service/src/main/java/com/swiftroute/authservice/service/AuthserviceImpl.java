package com.swiftroute.authservice.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.authservice.config.JwtConfig;
import com.swiftroute.authservice.dto.request.LoginRequestDTO;
import com.swiftroute.authservice.dto.request.RegisterRequestDTO;
import com.swiftroute.authservice.dto.request.UpdateProfileRequestDTO;
import com.swiftroute.authservice.dto.response.LoginResponseDTO;
import com.swiftroute.authservice.dto.response.UserProfileResponseDTO;
import com.swiftroute.authservice.entity.RefreshToken;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.exception.EmailAlreadyExistsException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.exception.InvalidCredentialsException;
import com.swiftroute.authservice.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthserviceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.swiftroute.authservice.repository.TenantMembershipRepository tenantMembershipRepository;

    // SỬA Ở ĐÂY: Dùng JwtService của bạn thay vì TokenService của Spring
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;

    @org.springframework.beans.factory.annotation.Autowired
    private EmailVerificationService emailVerificationService;

    @Override
    @Transactional
    public void register(RegisterRequestDTO request) {
        if (userRepository.findByEmail(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT)).isPresent()) {
            throw new EmailAlreadyExistsException(ErrorCode.USER_EXISTED);
        }

        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhoneNumber())
                .build();

        User saved = userRepository.save(user);
        userRepository.flush();
        if (emailVerificationService != null)
            emailVerificationService.resend(saved.getId());
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO request, HttpServletRequest httpRequest) {
        // 1. Dùng orElseThrow để lấy thẳng object User ra, khỏi check null dài dòng
        User user = userRepository.findByEmail(request.getUsername().trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS));
        // 2. Check trạng thái
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 4. Tạo token (Access Token hạn theo cấu hình, mặc định 15 phút, Refresh Token hạn 7 ngày) & user
        // profile
        long ttlMinutes = (jwtConfig != null && jwtConfig.getAccessTokenExpiryMinutes() > 0)
                ? jwtConfig.getAccessTokenExpiryMinutes()
                : 15;
        return buildAuthResponse(user, ttlMinutes, httpRequest);
    }

    @Override
    @Transactional
    public LoginResponseDTO refreshAccessToken(String refreshToken, HttpServletRequest httpRequest) {
        RefreshToken validatedOldToken = jwtService.validateAndRotate(refreshToken, httpRequest);

        User user = userRepository.findById(validatedOldToken.getUser().getId())
                .orElseThrow(() -> new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS);
        }

        long ttlMinutes = (jwtConfig != null && jwtConfig.getAccessTokenExpiryMinutes() > 0)
                ? jwtConfig.getAccessTokenExpiryMinutes()
                : 15;

        return buildAuthResponse(user, ttlMinutes, httpRequest);
    }

    private java.util.UUID extractRequestedTenantId(HttpServletRequest request) {
        if (request == null) return null;
        String header = request.getHeader("X-Tenant-Id");
        if (header != null && !header.isBlank()) {
            try { return java.util.UUID.fromString(header.trim()); } catch (IllegalArgumentException ignored) {}
        }
        String param = request.getParameter("tenantId");
        if (param == null || param.isBlank()) param = request.getParameter("tenant_id");
        if (param != null && !param.isBlank()) {
            try { return java.util.UUID.fromString(param.trim()); } catch (IllegalArgumentException ignored) {}
        }
        String authHeader = request.getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            String claim = jwtService.extractTenantId(token);
            if (claim != null && !claim.isBlank()) {
                try { return java.util.UUID.fromString(claim.trim()); } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    /**
     * Issues access/refresh tokens and populates the user profile snapshot
     * from the user's active tenant membership (retaining requested active tenant session if matched).
     */
    private LoginResponseDTO buildAuthResponse(User user, long ttlMinutes, HttpServletRequest httpRequest) {
        java.util.List<com.swiftroute.authservice.entity.TenantMembership> activeMemberships = tenantMembershipRepository
                .findByUser_IdAndStatus(user.getId(),
                        com.swiftroute.authservice.entity.enums.TenantMembershipStatus.ACTIVE);

        java.util.UUID requestedTenantId = extractRequestedTenantId(httpRequest);
        com.swiftroute.authservice.entity.TenantMembership selectedMembership = null;
        if (requestedTenantId != null) {
            selectedMembership = activeMemberships.stream()
                    .filter(m -> m.getTenantId().equals(requestedTenantId))
                    .findFirst()
                    .orElse(null);
        }
        if (selectedMembership == null && !activeMemberships.isEmpty()) {
            selectedMembership = activeMemberships.get(0);
        }

        String accessToken;
        String effectiveRole;
        java.util.UUID tenantId = null;

        if (selectedMembership != null) {
            effectiveRole = selectedMembership.getMembershipRole().name();
            tenantId = selectedMembership.getTenantId();
            accessToken = jwtService.generateTenantAccessToken(user, tenantId, effectiveRole, ttlMinutes);
        } else {
            effectiveRole = user.getRole().name();
            accessToken = jwtService.generateAccessToken(user, ttlMinutes);
        }

        String refreshToken = jwtService.generateAndStoreRefreshToken(user, httpRequest);

        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserProfile(user, effectiveRole, tenantId))
                .build();
    }

    private UserProfileResponseDTO toUserProfile(User user, String effectiveRole, java.util.UUID tenantId) {
        String resolvedRole = (effectiveRole == null || effectiveRole.isBlank())
                ? user.getRole().name()
                : effectiveRole;
        return new UserProfileResponseDTO(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getRole(),
                resolvedRole,
                tenantId,
                user.getStatus(),
                user.getCreatedAt(),
                user.isEmailVerified());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        jwtService.revokeToken(refreshToken);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public UserProfileResponseDTO getProfile(java.util.UUID userId, String effectiveRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.swiftroute.authservice.exception.AppException(ErrorCode.USER_NOT_FOUND));
        java.util.UUID tenantId = tenantMembershipRepository
                .findByUser_IdAndStatus(userId,
                        com.swiftroute.authservice.entity.enums.TenantMembershipStatus.ACTIVE)
                .stream().findFirst().map(com.swiftroute.authservice.entity.TenantMembership::getTenantId)
                .orElse(null);
        return toUserProfile(user, effectiveRole, tenantId);
    }

    @Override
    @Transactional
    public UserProfileResponseDTO updateProfile(java.util.UUID userId, UpdateProfileRequestDTO request,
            String effectiveRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.swiftroute.authservice.exception.AppException(ErrorCode.USER_NOT_FOUND));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phoneNumber().trim());
        userRepository.save(user);
        return getProfile(userId, effectiveRole);
    }
}
