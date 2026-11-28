package com.swiftroute.orderservice.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Service-level JWT token parser and validator.
 * Validates token signature with the shared symmetric key and extracts identity/tenant claims.
 */
@Component
@Slf4j
public class JwtTokenValidator {

    private final JwtParser jwtParser;

    public JwtTokenValidator(@Value("${jwt.secret:your_super_secret_key_change_in_production}") String jwtSecret) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser().verifyWith(key).build();
    }

    /**
     * Parses and returns claims from a Bearer token string.
     * Returns null if token is null, invalid, or expired.
     */
    public Claims parseAndValidate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7).trim();
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed in order-service: {}", e.getMessage());
            return null;
        }
    }
}

