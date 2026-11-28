package com.swiftroute.authservice.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.authservice.config.JwtConfig;
import com.swiftroute.authservice.dto.request.GoogleAuthRequest;
import com.swiftroute.authservice.dto.request.LoginRequestDTO;
import com.swiftroute.authservice.dto.request.RegisterRequestDTO;
import com.swiftroute.authservice.dto.request.UpdateProfileRequestDTO;
import com.swiftroute.authservice.dto.response.LoginResponseDTO;
import com.swiftroute.authservice.dto.response.UserProfileResponseDTO;
import com.swiftroute.authservice.entity.RefreshToken;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.EmailAlreadyExistsException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.exception.InvalidCredentialsException;
import com.swiftroute.authservice.repository.UserRepository;
import com.swiftroute.authservice.service.GoogleIdentityService.GoogleIdentity;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthserviceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.swiftroute.authservice.repository.TenantMembershipRepository tenantMembershipRepository;
    private final GoogleIdentityService googleIdentityService;
    // SỬA Ở ĐÂY: Dùng JwtService của bạn thay vì TokenService của Spring
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;

    @org.springframework.beans.factory.annotation.Autowired
    private EmailVerificationService emailVerificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TenantPermissionService tenantPermissionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DriverActivationService driverActivationService;

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
                .status("PENDING_VERIFICATION")
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
        if (!"ACTIVE".equals(user.getStatus()) || !user.isEmailVerified()) {
            throw new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 4. Tạo token (Access Token hạn theo cấu hình, mặc định 15 phút, Refresh Token
        // hạn 7 ngày) & user
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
        if (validatedOldToken.getSessionId() != null
                && driverActivationService != null
                && driverActivationService.isSessionRevoked(validatedOldToken.getSessionId())) {
            throw new com.swiftroute.authservice.exception.TokenExpiredException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = userRepository.findById(validatedOldToken.getUser().getId())
                .orElseThrow(() -> new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS));

        if (!"ACTIVE".equals(user.getStatus()) || !user.isEmailVerified()) {
            throw new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS);
        }

        long ttlMinutes = (jwtConfig != null && jwtConfig.getAccessTokenExpiryMinutes() > 0)
                ? jwtConfig.getAccessTokenExpiryMinutes()
                : 15;

        return buildAuthResponse(
                user,
                ttlMinutes,
                httpRequest,
                validatedOldToken.getActiveTenantId(),
                validatedOldToken.getSessionId());
    }

    private java.util.UUID extractRequestedTenantId(HttpServletRequest request) {
        if (request == null)
            return null;
        String header = request.getHeader("X-Tenant-Id");
        if (header != null && !header.isBlank()) {
            try {
                return java.util.UUID.fromString(header.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        String param = request.getParameter("tenantId");
        if (param == null || param.isBlank())
            param = request.getParameter("tenant_id");
        if (param != null && !param.isBlank()) {
            try {
                return java.util.UUID.fromString(param.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        String authHeader = request.getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            String claim = jwtService.extractTenantId(token);
            if (claim != null && !claim.isBlank()) {
                try {
                    return java.util.UUID.fromString(claim.trim());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * Issues access/refresh tokens and populates the user profile snapshot
     * from the user's active tenant membership (retaining requested active tenant
     * session if matched).
     */
    private LoginResponseDTO buildAuthResponse(User user, long ttlMinutes, HttpServletRequest httpRequest) {
        return buildAuthResponse(user, ttlMinutes, httpRequest, null);
    }

    private LoginResponseDTO buildAuthResponse(
            User user,
            long ttlMinutes,
            HttpServletRequest httpRequest,
            java.util.UUID fallbackTenantId) {
        return buildAuthResponse(user, ttlMinutes, httpRequest, fallbackTenantId, null);
    }

    private LoginResponseDTO buildAuthResponse(
            User user,
            long ttlMinutes,
            HttpServletRequest httpRequest,
            java.util.UUID fallbackTenantId,
            java.util.UUID sessionId) {
        java.util.List<com.swiftroute.authservice.entity.TenantMembership> activeMemberships = tenantMembershipRepository
                .findByUser_IdAndStatus(user.getId(),
                        com.swiftroute.authservice.entity.enums.TenantMembershipStatus.ACTIVE);

        java.util.UUID requestedTenantId = extractRequestedTenantId(httpRequest);
        if (requestedTenantId == null) {
            requestedTenantId = fallbackTenantId;
        }
        com.swiftroute.authservice.entity.TenantMembership selectedMembership = null;
        if (requestedTenantId != null) {
            final java.util.UUID targetTenantId = requestedTenantId;
            selectedMembership = activeMemberships.stream()
                    .filter(m -> m.getTenantId().equals(targetTenantId))
                    .findFirst()
                    .orElse(null);
        }
        if (selectedMembership == null && !activeMemberships.isEmpty()) {
            selectedMembership = activeMemberships.get(0);
        }

        String accessToken;
        String effectiveRole;
        java.util.UUID tenantId = null;
        java.util.List<String> effectivePermissions;

        if (selectedMembership != null) {
            effectiveRole = selectedMembership.getMembershipRole().name();
            tenantId = selectedMembership.getTenantId();
            if (sessionId != null) {
                effectivePermissions = tenantPermissionService != null
                        ? tenantPermissionService.resolveEffectivePermissions(user.getRole(), selectedMembership)
                        : com.swiftroute.authservice.entity.enums.TenantPermission
                                .defaultPermissionsForRoleName(effectiveRole, user.getRole());
                accessToken = jwtService.generateTenantAccessToken(
                        user,
                        tenantId,
                        effectiveRole,
                        effectivePermissions,
                        selectedMembership.getPermissionsVersion(),
                        sessionId,
                        ttlMinutes);
            } else if (tenantPermissionService != null) {
                effectivePermissions = tenantPermissionService.resolveEffectivePermissions(
                        user.getRole(), selectedMembership);
                accessToken = jwtService.generateTenantAccessToken(
                        user,
                        tenantId,
                        effectiveRole,
                        effectivePermissions,
                        selectedMembership.getPermissionsVersion(),
                        ttlMinutes);
            } else {
                effectivePermissions = com.swiftroute.authservice.entity.enums.TenantPermission
                        .defaultPermissionsForRoleName(effectiveRole, user.getRole());
                accessToken = jwtService.generateTenantAccessToken(user, tenantId, effectiveRole, ttlMinutes);
            }
        } else {
            effectiveRole = user.getRole().name();
            effectivePermissions = com.swiftroute.authservice.entity.enums.TenantPermission
                    .defaultPermissionsForRoleName(effectiveRole, user.getRole());
            accessToken = jwtService.generateAccessToken(user, ttlMinutes);
        }

        String refreshToken;
        if (sessionId != null) {
            refreshToken = jwtService.generateAndStoreRefreshToken(user, httpRequest, tenantId, sessionId);
        } else {
            refreshToken = (tenantPermissionService != null && tenantId != null)
                    ? jwtService.generateAndStoreRefreshToken(user, httpRequest, tenantId)
                    : jwtService.generateAndStoreRefreshToken(user, httpRequest);
        }

        Long permissionsVersion = selectedMembership != null ? selectedMembership.getPermissionsVersion() : null;

        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserProfile(user, effectiveRole, tenantId, effectivePermissions, permissionsVersion))
                .build();
    }

    private UserProfileResponseDTO toUserProfile(
            User user,
            String effectiveRole,
            java.util.UUID tenantId,
            java.util.List<String> effectivePermissions,
            Long permissionsVersion) {
        String resolvedRole = (effectiveRole == null || effectiveRole.isBlank())
                ? user.getRole().name()
                : effectiveRole;
        java.util.List<String> perms = effectivePermissions != null
                ? effectivePermissions
                : com.swiftroute.authservice.entity.enums.TenantPermission
                        .defaultPermissionsForRoleName(resolvedRole, user.getRole());
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
                user.isEmailVerified(),
                perms,
                permissionsVersion);
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
        java.util.List<com.swiftroute.authservice.entity.TenantMembership> activeMemberships = tenantMembershipRepository
                .findByUser_IdAndStatus(userId,
                        com.swiftroute.authservice.entity.enums.TenantMembershipStatus.ACTIVE);
        java.util.UUID requestedTenantId = resolveCurrentRequestTenantId();
        com.swiftroute.authservice.entity.TenantMembership activeMembership = null;
        if (requestedTenantId != null) {
            activeMembership = activeMemberships.stream()
                    .filter(m -> m.getTenantId().equals(requestedTenantId))
                    .findFirst()
                    .orElse(null);
        }
        if (activeMembership == null && !activeMemberships.isEmpty()) {
            activeMembership = activeMemberships.get(0);
        }
        java.util.UUID tenantId = activeMembership != null ? activeMembership.getTenantId() : null;
        String liveRole = (activeMembership != null
                && user.getRole() != com.swiftroute.authservice.entity.enums.Role.SYSTEM_ADMIN)
                        ? activeMembership.getMembershipRole().name()
                        : effectiveRole;
        java.util.List<String> effectivePermissions = (tenantPermissionService != null && activeMembership != null)
                ? tenantPermissionService.resolveEffectivePermissions(user.getRole(), activeMembership)
                : com.swiftroute.authservice.entity.enums.TenantPermission
                        .defaultPermissionsForRoleName(liveRole, user.getRole());
        Long permissionsVersion = activeMembership != null ? activeMembership.getPermissionsVersion() : null;
        return toUserProfile(user, liveRole, tenantId, effectivePermissions, permissionsVersion);
    }

    private java.util.UUID resolveCurrentRequestTenantId() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            return extractRequestedTenantId(servletAttrs.getRequest());
        }
        return null;
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

    @Transactional
    public void linkGoogle(UUID userId, GoogleAuthRequest request) {

        GoogleIdentity identity = googleIdentityService.verify(request.idToken());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!identity.emailVerified()) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (identity.sub().equals(user.getGoogleSub())) {
            return;
        }

        if (user.getGoogleSub() != null) {
            throw new AppException(
                    ErrorCode.GOOGLE_ALREADY_LINKED);
        }

        userRepository.findByGoogleSub(identity.sub())
                .ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(userId)) {
                        throw new AppException(
                                ErrorCode.GOOGLE_ACCOUNT_IN_USE);
                    }
                });

        userRepository.findByEmailIgnoreCase(identity.email())
                .ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(userId)) {
                        throw new AppException(
                                ErrorCode.GOOGLE_ACCOUNT_IN_USE);
                    }
                });

        user.setGoogleSub(identity.sub());

        userRepository.save(user);
    }

    @Override
    @Transactional
    public LoginResponseDTO loginWithGoogle(
            GoogleAuthRequest request,
            HttpServletRequest httpRequest) {
        GoogleIdentity identity = googleIdentityService.verify(request.idToken());

        if (!identity.emailVerified()) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = userRepository
                .findByGoogleSub(identity.sub())
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(identity.email())
                        // Conditional auto-link only when both Google and local account emails are verified
                        // and the account is active without an existing Google link (prevents pre-hijacking).
                        .filter(existing -> "ACTIVE".equals(existing.getStatus())
                                && existing.isEmailVerified()
                                && existing.getGoogleSub() == null)
                        .map(existing -> {
                            existing.setGoogleSub(identity.sub());
                            return userRepository.save(existing);
                        })
                        .orElseThrow(() -> new AppException(ErrorCode.GOOGLE_ACCOUNT_NOT_LINKED)));

        // Verify account status and email verification before issuing tokens
        if (!"ACTIVE".equals(user.getStatus()) || !user.isEmailVerified()) {
            throw new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS);
        }

        long ttlMinutes = (jwtConfig != null && jwtConfig.getAccessTokenExpiryMinutes() > 0)
                ? jwtConfig.getAccessTokenExpiryMinutes()
                : 15;

        return buildAuthResponse(
                user,
                ttlMinutes,
                httpRequest);
    }
}
