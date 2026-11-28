package com.swiftroute.authservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.swiftroute.authservice.entity.EmailVerificationToken;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.repository.EmailVerificationTokenRepository;
import com.swiftroute.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock private UserRepository users;
    @Mock private EmailVerificationTokenRepository tokens;
    @Mock private JdbcTemplate jdbc;
    @Mock private VerificationCipher cipher;
    @InjectMocks private EmailVerificationService service;

    @Test
    void verify_ValidToken_ActivatesPendingAccount() {
        UUID tokenId = UUID.randomUUID();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("pending@example.com")
                .status("PENDING_VERIFICATION")
                .emailVerified(false)
                .build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenId(tokenId)
                .user(user)
                .tokenHash(EmailVerificationService.hash("valid-token"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(jdbc.update(anyString(), any(UUID.class))).thenReturn(1);

        service.verify("valid-token");

        assertTrue(user.isEmailVerified());
        assertEquals("ACTIVE", user.getStatus());
        verify(users).save(user);
    }
}
