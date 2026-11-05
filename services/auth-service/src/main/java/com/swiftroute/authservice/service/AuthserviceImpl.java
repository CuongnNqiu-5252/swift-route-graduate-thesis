package com.swiftroute.authservice.service;

import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthserviceImpl implements AuthService {
    private static final Set<String> SELF_REGISTERABLE_ROLES = Set.of("CUSTOMER", "DRIVER");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.swiftroute.authservice.repository.TenantMembershipRepository tenantMembershipRepository;

    // SỬA Ở ĐÂY: Dùng JwtService của bạn thay vì TokenService của Spring
    private final JwtService jwtService;

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
        if (emailVerificationService != null) emailVerificationService.resend(saved.getId());
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

        // 4. Tạo token (Access Token hạn 15 phút, Refresh Token hạn 7 ngày)
        // 4. Tạo token: nếu user có membership ACTIVE trong tenant thì cấp token với role và tenant_id của membership đó
        java.util.List<com.swiftroute.authservice.entity.TenantMembership> activeMemberships = tenantMembershipRepository
                .findByUser_IdAndStatus(user.getId(), com.swiftroute.authservice.entity.enums.TenantMembershipStatus.ACTIVE);

        String accessToken;
        if (!activeMemberships.isEmpty()) {
            com.swiftroute.authservice.entity.TenantMembership primary = activeMemberships.get(0);
            String tenantRole = primary.getMembershipRole().name();
            accessToken = jwtService.generateTenantAccessToken(user, primary.getTenantId(), tenantRole, 15);
        } else {
            accessToken = jwtService.generateAccessToken(user, 15);
        }
        String refreshToken = jwtService.generateAndStoreRefreshToken(user, httpRequest); // Mượn tạm hàm tạo token để
                                                                                          // làm refresh

        // 5. Trả về DTO thuần (Sẽ được Controller gói vào ApiResponse sau)
        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    @Transactional
    public LoginResponseDTO refreshAccessToken(String refreshToken, HttpServletRequest httpRequest) {
        RefreshToken validatedOldToken = jwtService.validateAndRotate(refreshToken, httpRequest);

        // Use .orElseThrow() to unwrap the Optional into a User, and throw the
        // exception if empty
        User user = userRepository.findById(validatedOldToken.getUser().getId())
                .orElseThrow(() -> new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS));

        // Pass the unwrapped 'user' and use 60 (long) instead of 60.00 (double)
        java.util.List<com.swiftroute.authservice.entity.TenantMembership> activeMemberships = tenantMembershipRepository
                .findByUser_IdAndStatus(user.getId(), com.swiftroute.authservice.entity.enums.TenantMembershipStatus.ACTIVE);

        String newAccessToken;
        if (!activeMemberships.isEmpty()) {
            com.swiftroute.authservice.entity.TenantMembership primary = activeMemberships.get(0);
            String tenantRole = primary.getMembershipRole().name();
            newAccessToken = jwtService.generateTenantAccessToken(user, primary.getTenantId(), tenantRole, 60);
        } else {
            newAccessToken = jwtService.generateAccessToken(user, 60);
        }
        String newRefreshToken = jwtService.generateAndStoreRefreshToken(user, httpRequest);

        // Return the actual response instead of throwing an
        // UnsupportedOperationException
        return LoginResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
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
        return new UserProfileResponseDTO(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getRole(),
                effectiveRole == null || effectiveRole.isBlank() ? user.getRole().name() : effectiveRole,
                tenantMembershipRepository.findByUser_IdAndStatus(userId, com.swiftroute.authservice.entity.enums.TenantMembershipStatus.ACTIVE).stream().findFirst().map(com.swiftroute.authservice.entity.TenantMembership::getTenantId).orElse(null),
                user.getStatus(),
                user.getCreatedAt(), user.isEmailVerified());
    }

    @Override
    @Transactional
    public UserProfileResponseDTO updateProfile(java.util.UUID userId, UpdateProfileRequestDTO request, String effectiveRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.swiftroute.authservice.exception.AppException(ErrorCode.USER_NOT_FOUND));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phoneNumber().trim());
        userRepository.save(user);
        return getProfile(userId, effectiveRole);
    }
}
