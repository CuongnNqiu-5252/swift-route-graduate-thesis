package com.swiftroute.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtVerifierTest {

    private static final String TEST_SECRET = "swiftroute-test-jwt-secret-key-at-least-32-bytes";

    private SecretKey signingKey;
    private JwtVerifier jwtVerifier;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        JwtParser parser = Jwts.parser().verifyWith(signingKey).build();
        jwtVerifier = new JwtVerifier(parser);
    }

    @Test
    @DisplayName("Extracts sub, system_role, tenant_id, and tenant_role from tenant-scoped JWT")
    void extractsTenantScopedClaims() {
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String token = Jwts.builder()
                .subject(userId)
                .claim("system_role", "USER")
                .claim("tenant_id", tenantId)
                .claim("tenant_role", "TENANT_ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(signingKey)
                .compact();

        JwtClaims claims = jwtVerifier.verify(token).block();

        assertThat(claims).isNotNull();
        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.systemRole()).isEqualTo("USER");
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.tenantRole()).isEqualTo("TENANT_ADMIN");
        assertThat(claims.effectiveRole()).isEqualTo("TENANT_ADMIN");
    }

    @Test
    @DisplayName("Extracts system_role with null tenant claims when token has no tenant scope")
    void extractsSystemOnlyClaims() {
        String userId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String token = Jwts.builder()
                .subject(userId)
                .claim("system_role", "SYSTEM_ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(signingKey)
                .compact();

        JwtClaims claims = jwtVerifier.verify(token).block();

        assertThat(claims).isNotNull();
        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.systemRole()).isEqualTo("SYSTEM_ADMIN");
        assertThat(claims.tenantId()).isNull();
        assertThat(claims.tenantRole()).isNull();
        assertThat(claims.effectiveRole()).isEqualTo("SYSTEM_ADMIN");
    }
}

