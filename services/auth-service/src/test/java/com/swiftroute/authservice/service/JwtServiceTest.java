package com.swiftroute.authservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.authservice.config.JwtConfig;
import com.swiftroute.authservice.entity.RefreshToken;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.Role;
import com.swiftroute.authservice.exception.TokenReuseDetectedException;
import com.swiftroute.authservice.repository.RefreshTokenRepository;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
public class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-characters-long!";

    // Sử dụng SecretKey thật thay vì Mock, vì Jwts cần key thật để mã hóa/giải mã
    // token
    private SecretKey secretKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(TEST_SECRET.getBytes());

    @Mock
    private JwtConfig jwtConfig;
    @Mock
    HttpServletRequest request;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtService jwtService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Khởi tạo JwtService thủ công để truyền secretKey thật vào
        jwtService = new JwtService(secretKey, jwtConfig, refreshTokenRepository);
    }

    @Test // Bỏ chữ 'private' đi, JUnit 5 yêu cầu method test phải có modifier là default
          // hoặc public
    void generateAccessToken_returnsTokenWithCorrectClaims() {
        java.util.UUID userId = java.util.UUID.randomUUID();
        User user = User.builder()
                .id(userId) // Cần set ID vì extractSubject sẽ lấy ID
                .email("test@example.com")
                .passwordHash("password")
                .role(Role.CUSTOMER)
                .build();

        String token = jwtService.generateAccessToken(user, 15);

        // extractSubject trả về String, nên cần gọi toString() trên UUID
        assertEquals(userId.toString(), jwtService.extractSubject(token));

        // extractRole trả về String, nên cần so sánh với chuỗi
        assertEquals(Role.CUSTOMER.name(), jwtService.extractRole(token));
    }

    @Test
    void validateToken_withValidToken_returnsTrue() {
        java.util.UUID userId = java.util.UUID.randomUUID();
        User user = User.builder().id(userId).role(Role.CUSTOMER).build();

        String validToken = jwtService.generateAccessToken(user, 15);

        boolean isValid = jwtService.validateToken(validToken);

        assertTrue(isValid);
    }

    @Test
    void generateAndStoreRefreshToken_withValidUser_returnsToken() {
        User user = User.builder().id(java.util.UUID.randomUUID()).build();
        when(jwtConfig.getRefreshTokenExpiryDays()).thenReturn(7L);

        String token = jwtService.generateAndStoreRefreshToken(user, null);

        assertNotNull(token);
        verify(refreshTokenRepository).save(argThat(rt -> rt.getUser().equals(user)
                && !rt.isRevoked()
                && rt.getExpiresAt().isAfter(LocalDateTime.now())));

    }

    @Test
    void extractSubject_withValidToken_returnsCorrectSubject() {
        java.util.UUID userId = java.util.UUID.randomUUID();
        User user = User.builder().id(userId).role(Role.CUSTOMER).build();

        String token = jwtService.generateAccessToken(user, 15);

        assertEquals(userId.toString(), jwtService.extractSubject(token));
    }

    @Test
    void extractRole_withValidToken_returnsCorrectRole() {
        java.util.UUID userId = java.util.UUID.randomUUID();
        User user = User.builder().id(userId).role(Role.CUSTOMER).build();

        String token = jwtService.generateAccessToken(user, 15);

        assertEquals(Role.CUSTOMER.name(), jwtService.extractRole(token));
    }

    @Test
    void validateToken_withExpiredToken_returnsFalse() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .role(Role.CUSTOMER)
                .build();
        String expiredToken = jwtService.generateAccessToken(user, -1);
        boolean isValid = jwtService.validateToken(expiredToken);
        assertFalse(isValid);
    }

    @Test
    void validateToken_withInvalidToken_returnsFalse() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .role(Role.CUSTOMER)
                .build();
        String invalidToken = "super-secret-junk";
        boolean isValid = jwtService.validateToken(invalidToken);
        assertFalse(isValid);
    }

    @Test
    void refreshToken_withValidToken() {
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenId(null)
                .tokenHash("some-hash")
                .expiresAt(LocalDateTime.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));
        RefreshToken newToken = jwtService.validateAndRotate("my-plain-refresh-token", request);
        assertNotNull(newToken);
        verify(refreshTokenRepository).save(argThat(rt -> rt.getTokenHash().equals("some-hash")
                && rt.isRevoked()));

    }

    @Test
    void refershToken_withTokenRevoked() {
        User user = User.builder().id(UUID.randomUUID()).build();
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenId(null)
                .tokenHash("valid-token-hash")
                .user(user)
                .expiresAt(LocalDateTime.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));
        assertThrows(TokenReuseDetectedException.class, () -> {
            jwtService.validateAndRotate("my-plain-refresh-token", request);
        });
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }

}
