package com.swiftroute.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.authservice.entity.PasswordResetToken;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.repository.PasswordResetTokenRepository;
import com.swiftroute.authservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final VerificationCipher cipher;

    @Value("${password-reset.url:http://localhost:3000/reset-password}")
    private String resetUrl = "http://localhost:3000/reset-password";

    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) return;
        users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).ifPresent(user -> {
            Instant now = Instant.now();
            if (tokens.existsByUser_IdAndCreatedAtAfter(user.getId(), now.minusSeconds(60))) return;
            for (var previous : tokens.findAllByUser_IdAndUsedAtIsNull(user.getId())) {
                previous.setUsedAt(now);
            }
            String raw = randomToken();
            tokens.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(hash(raw))
                    .expiresAt(now.plusSeconds(900))
                    .build());
            String separator = resetUrl.contains("?") ? "&" : "?";
            String link = resetUrl + separator + "token=" + raw;
            jdbc.update("""
                    INSERT INTO auth_notification_outbox(event_id,user_id,email,encrypted_body,event_type)
                    VALUES (?,?,?,?,?)
                    """, UUID.randomUUID(), user.getId(), user.getEmail(), cipher.encrypt(link),
                    "user.password_reset_requested.v1");
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256
                || newPassword == null || newPassword.length() < 8 || newPassword.length() > 100) {
            throw new AppException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }
        var token = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));
        if (tokens.consume(token.getTokenId(), Instant.now()) != 1) {
            throw new AppException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }
        var user = token.getUser();
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        jdbc.update("UPDATE refresh_token SET revoked=true WHERE user_id=? AND revoked=false", user.getId());
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
