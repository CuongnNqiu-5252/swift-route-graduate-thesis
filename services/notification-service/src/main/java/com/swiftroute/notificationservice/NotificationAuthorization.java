package com.swiftroute.notificationservice;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Stateless JWT authorizer for notification API endpoints.
 * Requires either SYSTEM_ADMIN or a tenant-scoped JWT matching the requested tenant.
 */
@Component
public class NotificationAuthorization {

    private static final Set<String> OPERATOR_ROLES = Set.of("OWNER", "TENANT_ADMIN", "OPERATOR");
    private static final Set<String> MEMBER_ROLES = Set.of("OWNER", "TENANT_ADMIN", "OPERATOR", "DRIVER");

    private final JwtParser jwtParser;

    @Autowired
    public NotificationAuthorization(
            @Value("${jwt.secret:your_super_secret_key_change_in_production}") String jwtSecret) {
        this(buildJwtParser(jwtSecret));
    }

    NotificationAuthorization(JwtParser jwtParser) {
        this.jwtParser = jwtParser;
    }

    static JwtParser buildJwtParser(String jwtSecret) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build();
    }

    public record AuthorizedContext(UUID userId, UUID tenantId) {}

    public void requireOperator(String authorization, UUID tenantId) {
        authorize(authorization, tenantId, OPERATOR_ROLES);
    }

    public UUID resolveOperatorTenantId(String authorization, UUID requestedTenantId) {
        return authorize(authorization, requestedTenantId, OPERATOR_ROLES).tenantId();
    }

    public UUID requireMemberUserId(String authorization, UUID tenantId) {
        return authorize(authorization, tenantId, MEMBER_ROLES).userId();
    }

    public AuthorizedContext resolveMember(String authorization, UUID requestedTenantId) {
        return authorize(authorization, requestedTenantId, MEMBER_ROLES);
    }

    private AuthorizedContext authorize(String authorization, UUID requestedTenantId, Set<String> allowedTenantRoles) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = jwtParser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        UUID userId = parseUserId(claims.getSubject());
        String tokenTenantId = claims.get("tenant_id", String.class);
        UUID jwtTenantId = parseOptionalTenantId(tokenTenantId);
        String systemRole = claims.get("system_role", String.class);
        if ("SYSTEM_ADMIN".equalsIgnoreCase(systemRole)) {
            UUID resolvedTenantId = requestedTenantId != null ? requestedTenantId : jwtTenantId;
            if (resolvedTenantId == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Target tenant context required");
            }
            return new AuthorizedContext(userId, resolvedTenantId);
        }

        String tokenTenantRole = claims.get("tenant_role", String.class);

        if (jwtTenantId == null
                || (requestedTenantId != null && !requestedTenantId.equals(jwtTenantId))
                || tokenTenantRole == null || tokenTenantRole.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant-scoped token required");
        }

        if (!allowedTenantRoles.contains(tokenTenantRole.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return new AuthorizedContext(userId, jwtTenantId);
    }

    private UUID parseOptionalTenantId(String tokenTenantId) {
        if (tokenTenantId == null || tokenTenantId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(tokenTenantId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid tenant scope");
        }
    }

    private UUID parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
