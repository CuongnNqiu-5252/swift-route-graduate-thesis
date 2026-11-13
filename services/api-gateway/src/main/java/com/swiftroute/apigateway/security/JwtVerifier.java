package com.swiftroute.apigateway.security;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Component đóng gói logic verify & parse JWT token.
 *
 * Tách ra khỏi JwtAuthFilter để:
 * 1. Dễ unit test độc lập
 * 2. Có thể mock trong test Gateway filter
 *
 * Lý do dùng Mono.fromCallable + Schedulers.boundedElastic():
 * - jjwt parseSignedClaims() là blocking I/O (CPU-bound crypto op)
 * - Trong WebFlux reactive pipeline, blocking calls cần được offload
 * sang bounded elastic thread pool để không block event loop
 *
 * Claims match what auth-service's JwtService embeds into tokens:
 * - subject       = userId
 * - "system_role" = systemRole (SYSTEM_ADMIN, USER)
 * - "tenant_id"   = tenantId (nullable)
 * - "tenant_role" = tenantRole (nullable)
 */
@Component
public class JwtVerifier {

    private final JwtParser jwtParser;

    public JwtVerifier(JwtParser jwtParser) {
        this.jwtParser = jwtParser;
    }

    /**
     * Verify token and return parsed JwtClaims when valid.
     * Throws an exception (handled by onErrorResume in JwtAuthFilter)
     * when the token is expired, has an invalid signature, or is malformed.
     */
    public Mono<JwtClaims> verify(String token) {
        return Mono.fromCallable(() -> {
            Claims claims = jwtParser
                    .parseSignedClaims(token)
                    .getPayload();

            return new JwtClaims(
                    claims.getSubject(),
                    claims.get("system_role", String.class),
                    claims.get("tenant_id", String.class),
                    claims.get("tenant_role", String.class)
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
