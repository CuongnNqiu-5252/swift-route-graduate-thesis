package com.swiftroute.notificationservice;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Set<String> OPERATOR_ROLES = Set.of("OWNER", "TENANT_ADMIN", "OPERATOR", "VIEWER");
    private static final Set<String> MEMBER_ROLES = Set.of("OWNER", "TENANT_ADMIN", "OPERATOR", "DRIVER");
    private static final Map<String, LivePermissionSnapshot> LIVE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> REVOKED_SESSION_CACHE = new ConcurrentHashMap<>();

    public static void recordRevokedSession(UUID sessionId, long expiresAtEpochSec) {
        if (sessionId == null) {
            return;
        }
        long nowSec = System.currentTimeMillis() / 1000L;
        REVOKED_SESSION_CACHE.put(sessionId, Math.max(expiresAtEpochSec, nowSec + 7200L));
    }

    public static boolean isSessionRevoked(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        Long exp = REVOKED_SESSION_CACHE.get(sessionId);
        if (exp == null) {
            return false;
        }
        if (exp <= System.currentTimeMillis() / 1000L) {
            REVOKED_SESSION_CACHE.remove(sessionId);
            return false;
        }
        return true;
    }

    public record LivePermissionSnapshot(
            String membershipRole,
            String status,
            Set<String> permissions,
            long permissionsVersion) {
    }

    public static void recordLiveSnapshot(
            UUID tenantId,
            UUID userId,
            String membershipRole,
            String status,
            Set<String> permissions,
            long permissionsVersion) {
        if (tenantId == null || userId == null) {
            return;
        }
        String key = tenantId.toString() + ":" + userId.toString();
        LIVE_CACHE.compute(key, (k, existing) -> {
            if (existing != null && existing.permissionsVersion() > permissionsVersion) {
                return existing;
            }
            return new LivePermissionSnapshot(
                    membershipRole != null ? membershipRole.toUpperCase(Locale.ROOT) : "OPERATOR",
                    status != null ? status.toUpperCase(Locale.ROOT) : "ACTIVE",
                    permissions != null ? Set.copyOf(permissions) : Set.of(),
                    permissionsVersion);
        });
    }

    public static LivePermissionSnapshot getLiveSnapshot(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            return null;
        }
        return LIVE_CACHE.get(tenantId.toString() + ":" + userId.toString());
    }

    public static void clearCache() {
        LIVE_CACHE.clear();
    }

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

    public long requireSystemAdmin(String authorization) {
        Claims claims = parseClaims(authorization);
        parseUserId(claims.getSubject());
        if (!"SYSTEM_ADMIN".equalsIgnoreCase(claims.get("system_role", String.class))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (claims.getExpiration() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        long lifetime = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (lifetime <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return lifetime;
    }

    private Claims parseClaims(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            String sid = claims.get("sid", String.class);
            if (sid != null && !sid.isBlank() && isSessionRevoked(UUID.fromString(sid.trim()))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session has been revoked");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

    }

    private AuthorizedContext authorize(String authorization, UUID requestedTenantId, Set<String> allowedTenantRoles) {
        Claims claims = parseClaims(authorization);
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

        String effectiveRole = tokenTenantRole.toUpperCase(Locale.ROOT);
        Number tokenVerNum = claims.get("perm_ver", Number.class);
        long tokenVer = tokenVerNum != null ? tokenVerNum.longValue() : 1L;
        LivePermissionSnapshot live = getLiveSnapshot(jwtTenantId, userId);
        if (live != null && live.permissionsVersion() > tokenVer) {
            if (!"ACTIVE".equalsIgnoreCase(live.status())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membership is not active");
            }
            effectiveRole = live.membershipRole();
        }

        Set<String> explicitPermissions = extractExplicitPermissions(claims, jwtTenantId, userId, tokenVer, live);
        if (explicitPermissions != null && !"OWNER".equalsIgnoreCase(effectiveRole)) {
            if (!explicitPermissions.contains("notification:read")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }

        if (!allowedTenantRoles.contains(effectiveRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return new AuthorizedContext(userId, jwtTenantId);
    }

    private Set<String> extractExplicitPermissions(
            Claims claims,
            UUID tenantId,
            UUID userId,
            long tokenVer,
            LivePermissionSnapshot live) {
        if (live != null && live.permissionsVersion() > tokenVer) {
            return live.permissions();
        }
        Object raw = claims.get("permissions");
        if (raw instanceof List<?> list) {
            Set<String> set = new LinkedHashSet<>();
            for (Object item : list) {
                if (item instanceof String s && !s.isBlank()) {
                    set.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
            return Collections.unmodifiableSet(set);
        }
        return null;
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
