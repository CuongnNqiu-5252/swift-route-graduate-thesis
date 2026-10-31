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
 * Claims được parse tương ứng với những gì JwtService.generateAccessToken()
 * của auth-service đặt vào:
 * - subject = userId
 * - "_ROLE" = role
 * - "tenant_id" = tenantId
 */
@Component
public class JwtVerifier {

    private final JwtParser jwtParser;

    public JwtVerifier(JwtParser jwtParser) {
        this.jwtParser = jwtParser;
    }

    /**
     * Verify token và trả về JwtClaims nếu hợp lệ.
     * Throw exception (được onErrorResume xử lý trong JwtAuthFilter)
     * nếu token hết hạn, sai chữ ký, hoặc malformed.
     */
    public Mono<JwtClaims> verify(String token) {
        return Mono.fromCallable(() -> {
            Claims claims = jwtParser
                    .parseSignedClaims(token)
                    .getPayload();

            return new JwtClaims(
                    claims.getSubject(), // userId (UUID string)
                    claims.get("_ROLE", String.class), // role enum name
                    claims.get("tenant_id", String.class) // tenantId (nullable)
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
