package com.swiftroute.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.swiftroute.authservice.entity.PasswordResetToken;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.repository.PasswordResetTokenRepository;
import com.swiftroute.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock UserRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock JdbcTemplate jdbc;
    @Mock PasswordEncoder encoder;
    @Mock VerificationCipher cipher;
    @InjectMocks PasswordResetService service;

    @Test
    void unknownEmailDoesNotRevealAccountOrCreateToken() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        service.requestReset("missing@example.com");

        verify(tokens, never()).save(any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void resetTokenIsHashedAndSingleUseBeforePasswordChanges() {
        var user = User.builder().id(UUID.randomUUID()).email("driver@example.com").passwordHash("old").build();
        var token = PasswordResetToken.builder()
                .tokenId(UUID.randomUUID()).user(user).tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(tokens.consume(any(), any())).thenReturn(1);
        when(encoder.encode("new-password-123")).thenReturn("new-hash");

        service.resetPassword("raw-token", "new-password-123");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(users).save(user);
        verify(jdbc).update("UPDATE refresh_token SET revoked=true WHERE user_id=? AND revoked=false", user.getId());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(tokens).findByTokenHash(hash.capture());
        assertThat(hash.getValue()).doesNotContain("raw-token").hasSize(64);
    }
}
