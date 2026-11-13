package com.swiftroute.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class NotificationAuthorizationTest {

    private static final String TEST_SECRET = "swiftroute-test-jwt-secret-key-at-least-32-bytes";

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private SecretKey signingKey;
    private NotificationAuthorization authorization;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        authorization = new NotificationAuthorization(NotificationAuthorization.buildJwtParser(TEST_SECRET));
    }

    private String signToken(String systemRole, String tokenTenantId, String tenantRole) {
        Instant now = Instant.now();
        var jwt = Jwts.builder()
                .subject(userId.toString())
                .claim("system_role", systemRole)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)));
        if (tokenTenantId != null) {
            jwt.claim("tenant_id", tokenTenantId);
        }
        if (tenantRole != null) {
            jwt.claim("tenant_role", tenantRole);
        }
        return "Bearer " + jwt.signWith(signingKey).compact();
    }

    @Test
    void tenantScopedOperatorJwtAuthorizesRequestedTenant() {
        String bearer = signToken("USER", tenantId.toString(), "TENANT_ADMIN");
        authorization.requireOperator(bearer, tenantId);
    }

    @Test
    void systemAdminJwtAuthorizesAnyTenant() {
        String bearer = signToken("SYSTEM_ADMIN", null, null);
        authorization.requireOperator(bearer, tenantId);
    }

    @Test
    void tenantScopedDriverJwtCannotReadOperatorNotifications() {
        String bearer = signToken("USER", tenantId.toString(), "DRIVER");

        assertThatThrownBy(() -> authorization.requireOperator(bearer, tenantId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void tenantScopedDriverJwtAuthorizesDriverInboxAndReturnsVerifiedUserId() {
        String bearer = signToken("USER", tenantId.toString(), "DRIVER");

        UUID resolved = authorization.requireMemberUserId(bearer, tenantId);

        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    void unscopedJwtWithoutTenantScopeIsRejectedWithForbidden() {
        String bearer = signToken("USER", null, null);

        assertThatThrownBy(() -> authorization.requireOperator(bearer, tenantId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void jwtForDifferentTenantCannotAccessRequestedTenant() {
        String bearer = signToken("USER", UUID.randomUUID().toString(), "OWNER");

        assertThatThrownBy(() -> authorization.requireOperator(bearer, tenantId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void invalidSignatureJwtIsRejectedWithUnauthorized() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("wrong-signing-key-must-be-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        String forgedToken = "Bearer " + Jwts.builder()
                .subject(userId.toString())
                .claim("system_role", "SYSTEM_ADMIN")
                .signWith(wrongKey)
                .compact();

        assertThatThrownBy(() -> authorization.requireOperator(forgedToken, tenantId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void tenantScopedOperatorJwtWithoutHeaderUsesJwtTenantId() {
        String bearer = signToken("USER", tenantId.toString(), "TENANT_ADMIN");
        assertThat(authorization.resolveOperatorTenantId(bearer, null)).isEqualTo(tenantId);
    }

    @Test
    void tenantScopedDriverJwtWithoutHeaderUsesJwtUserAndTenantId() {
        String bearer = signToken("USER", tenantId.toString(), "DRIVER");
        var context = authorization.resolveMember(bearer, null);
        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void systemAdminWithoutHeaderOrTokenTenantIsRejectedWithForbidden() {
        String bearer = signToken("SYSTEM_ADMIN", null, null);
        assertThatThrownBy(() -> authorization.resolveOperatorTenantId(bearer, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void missingBearerIsRejectedWithUnauthorized() {
        assertThatThrownBy(() -> authorization.requireOperator(null, tenantId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
