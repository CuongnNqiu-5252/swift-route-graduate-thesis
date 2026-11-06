package com.swiftroute.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.swiftroute.authservice.entity.EmailVerificationToken;
import com.swiftroute.authservice.repository.*;
import com.swiftroute.authservice.exception.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final UserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final JdbcTemplate jdbc;
    private final VerificationCipher cipher;
    @Value("${verification.web-url:http://localhost:3000}") private String webUrl;

    @Transactional
    public void resend(UUID userId) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + (long)userId.hashCode() + ")");
        var user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.isEmailVerified()) return;
        Integer recent = jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens WHERE user_id=? AND created_at > now() - interval '60 seconds'", Integer.class, userId);
        if (recent != null && recent > 0) throw new AppException(ErrorCode.VERIFICATION_RATE_LIMITED);
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("UPDATE email_verification_tokens SET used_at=now() WHERE user_id=? AND used_at IS NULL",userId);
        tokens.save(EmailVerificationToken.builder().user(user).tokenHash(hash(raw)).expiresAt(Instant.now().plusSeconds(86400)).build());
        // Only hashes are retained in the token table; the delivery payload is AES-GCM encrypted at rest.
        String link = webUrl + "/verify-email?token=" + raw;
        jdbc.update("INSERT INTO auth_notification_outbox(event_id,user_id,email,encrypted_body) VALUES (?,?,?,?)", UUID.randomUUID(),userId,user.getEmail(),cipher.encrypt(link));
    }

    @Transactional
    public void resendByEmail(String email) {
        if (email == null || email.isBlank()) return;
        users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).ifPresent(user -> {
            try { resend(user.getId()); }
            catch (AppException ex) {
                if (ex.getErrorCode() != ErrorCode.VERIFICATION_RATE_LIMITED) throw ex;
            }
        });
    }

    @Transactional
    public void verify(String raw) {
        if (raw == null || raw.length() > 256) throw new AppException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        var token = tokens.findByTokenHash(hash(raw)).orElseThrow(() -> new AppException(ErrorCode.INVALID_VERIFICATION_TOKEN));
        int consumed = jdbc.update("UPDATE email_verification_tokens SET used_at=now() WHERE token_id=? AND used_at IS NULL AND expires_at > now()", token.getTokenId());
        if (consumed != 1) throw new AppException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        var user = token.getUser(); user.setEmailVerified(true); user.setEmailVerifiedAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()));
        users.save(user);
    }

    static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
