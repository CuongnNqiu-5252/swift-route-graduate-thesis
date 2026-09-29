package com.swiftroute.authservice.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.swiftroute.authservice.config.JwtConfig;
import com.swiftroute.authservice.entity.RefreshToken;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.exception.TokenExpiredException;
import com.swiftroute.authservice.exception.TokenReuseDetectedException;
import com.swiftroute.authservice.repository.RefreshTokenRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class JwtService {

    // Inject trực tiếp SecretKey (đã khai báo @Bean ở JwtConfig)
    private final SecretKey secretKey;
    private final JwtParser jwtParser;
    private final SecureRandom secureRandom = new SecureRandom();
    private final JwtConfig jwtConfig;
    private final RefreshTokenRepository refreshTokenRepository;

    public JwtService(SecretKey secretKey, JwtConfig jwtConfig, RefreshTokenRepository refreshTokenRepository) {
        this.secretKey = secretKey;
        this.jwtConfig = jwtConfig;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtParser = Jwts.parser().verifyWith(secretKey).build();
    }

    public String generateAccessToken(User user, long ttlMinutes) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("_ROLE", user.getRole().toString())
                .claim("tenant_id", user.getTenantId() != null ? user.getTenantId().toString() : null)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMinutes * 60 * 1000))
                .signWith(secretKey)
                .compact();
    }

    private String generateSecureRandomToken() {
        byte[] randomBytes = new byte[32]; // 256-bit
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public RefreshToken validateAndRotate(String plainRefreshToken, HttpServletRequest request) {
        String hash = sha256(plainRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new TokenExpiredException(ErrorCode.TOKEN_EXPIRED));

        if (existing.isRevoked()) {
            // Token này đã bị rotate/thu hồi trước đó nhưng vẫn bị dùng lại -> khả năng bị
            // đánh cắp
            refreshTokenRepository.revokeAllByUserId(existing.getUser().getId());
            throw new TokenReuseDetectedException(
                    ErrorCode.TOKEN_EXPIRED);
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException(ErrorCode.TOKEN_EXPIRED);
        }

        // Rotation: thu hồi token cũ
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return existing; // caller (AuthServiceImpl) sẽ dùng existing.getUserId()
    }

    public void revokeToken(String refreshToken) {
        String hash = sha256(refreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public String generateAndStoreRefreshToken(User user, HttpServletRequest request) {
        String plainToken = generateSecureRandomToken();
        String hash = sha256(plainToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plus(jwtConfig.getRefreshTokenExpiryDays(), ChronoUnit.DAYS))
                .revoked(false)
                .deviceInfo(request != null ? request.getHeader("User-Agent") : null)
                .ipAddress(request != null ? request.getRemoteAddr() : null)
                .build();

        refreshTokenRepository.save(refreshToken);
        return plainToken;
    }

    public boolean validateToken(String token) {
        try {
            jwtParser.parseSignedClaims(token);

            return true;
        } catch (JwtException e) {
            System.err.println("JWT Validation failed: " + e.getMessage());
            return false;

        } catch (IllegalArgumentException e) {
            System.err.println("JWT Token is empty: " + e.getMessage());
            return false;
        }

    }

    public String extractSubject(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public String extractRole(String token) {
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();
        return claims.get("_ROLE", String.class);
    }
}
