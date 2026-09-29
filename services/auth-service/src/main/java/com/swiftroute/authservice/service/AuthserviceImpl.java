package com.swiftroute.authservice.service;

import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.swiftroute.authservice.dto.request.LoginRequestDTO;
import com.swiftroute.authservice.dto.request.RegisterRequestDTO;
import com.swiftroute.authservice.dto.response.LoginResponseDTO;
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
    private static final Set<String> SELF_REGISTERABLE_ROLES = Set.of("CUSTOMER", "DRIVER");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // SỬA Ở ĐÂY: Dùng JwtService của bạn thay vì TokenService của Spring
    private final JwtService jwtService;

    @Override
    public void register(RegisterRequestDTO request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(ErrorCode.USER_EXISTED);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhoneNumber())
                .build();

        userRepository.save(user);
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO request, HttpServletRequest httpRequest) {
        // 1. Dùng orElseThrow để lấy thẳng object User ra, khỏi check null dài dòng
        User user = userRepository.findByEmail(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS));

        // 2. Check trạng thái
        if ("INACTIVE".equals(user.getStatus())) {
            throw new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 4. Tạo token (Access Token hạn 15 phút, Refresh Token hạn 7 ngày)
        String accessToken = jwtService.generateAccessToken(user, 15);
        String refreshToken = jwtService.generateAndStoreRefreshToken(user, httpRequest); // Mượn tạm hàm tạo token để
                                                                                          // làm refresh

        // 5. Trả về DTO thuần (Sẽ được Controller gói vào ApiResponse sau)
        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public LoginResponseDTO refreshAccessToken(String refreshToken, HttpServletRequest httpRequest) {
        RefreshToken validatedOldToken = jwtService.validateAndRotate(refreshToken, httpRequest);

        // Use .orElseThrow() to unwrap the Optional into a User, and throw the
        // exception if empty
        User user = userRepository.findById(validatedOldToken.getUser().getId())
                .orElseThrow(() -> new InvalidCredentialsException(ErrorCode.INVALID_CREDENTIALS));

        // Pass the unwrapped 'user' and use 60 (long) instead of 60.00 (double)
        String newAccessToken = jwtService.generateAccessToken(user, 60);
        String newRefreshToken = jwtService.generateAndStoreRefreshToken(user, httpRequest);

        // Return the actual response instead of throwing an
        // UnsupportedOperationException
        return LoginResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Override
    public void logout(String refreshToken) {
        jwtService.revokeToken(refreshToken);
    }

}
