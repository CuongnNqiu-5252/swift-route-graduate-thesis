package com.swiftroute.driverservice.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Maintains live member permission overrides synchronized via RabbitMQ
 * ("tenant.member_permissions_updated.v1") and maps driver-service HTTP
 * endpoints to required granular permissions.
 */
@Component
@Slf4j
public class TenantPermissionRegistry {

    public record LivePermissionSnapshot(
            String membershipRole,
            String status,
            Set<String> permissions,
            long permissionsVersion) {
    }

    private static final Map<String, LivePermissionSnapshot> LIVE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> REVOKED_SESSION_CACHE = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void recordRevokedSession(UUID sessionId, long expiresAtEpochSec) {
        if (sessionId == null) {
            return;
        }
        long nowSec = System.currentTimeMillis() / 1000L;
        if (REVOKED_SESSION_CACHE.size() > 5000) {
            REVOKED_SESSION_CACHE.entrySet().removeIf(entry -> entry.getValue() <= nowSec);
        }
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
        String key = cacheKey(tenantId, userId);
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
        return LIVE_CACHE.get(cacheKey(tenantId, userId));
    }

    public static void clearCache() {
        LIVE_CACHE.clear();
    }

    private static String cacheKey(UUID tenantId, UUID userId) {
        return tenantId.toString() + ":" + userId.toString();
    }

    @RabbitListener(queues = "driver.member-permissions-sync")
    public void onPermissionsUpdated(Message message) {
        try {
            JsonNode node = MAPPER.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            String revokedSid = node.path("revokedSessionId").asText(node.path("revoked_session_id").asText(""));
            if (!revokedSid.isBlank()) {
                long expSec = node.path("revokedSessionExpiresAtEpochSec")
                        .asLong((System.currentTimeMillis() / 1000L) + 7200L);
                recordRevokedSession(UUID.fromString(revokedSid.trim()), expSec);
                return;
            }
            String tenantStr = node.path("tenantId").asText(node.path("tenant_id").asText(""));
            String userStr = node.path("userId").asText(node.path("user_id").asText(""));
            if (tenantStr.isBlank() || userStr.isBlank()) {
                return;
            }
            UUID tenantId = UUID.fromString(tenantStr.trim());
            UUID userId = UUID.fromString(userStr.trim());
            String role = node.path("membershipRole").asText(node.path("membership_role").asText("OPERATOR"));
            String status = node.path("status").asText("ACTIVE");
            long version = node.path("permissionsVersion").asLong(node.path("permissions_version").asLong(1L));

            Set<String> perms = new LinkedHashSet<>();
            JsonNode permsNode = node.get("permissions");
            if (permsNode != null && permsNode.isArray()) {
                for (JsonNode item : permsNode) {
                    if (item.isTextual() && !item.asText().isBlank()) {
                        perms.add(item.asText().trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            recordLiveSnapshot(tenantId, userId, role, status, perms, version);
            log.info("Synchronized live permissions in driver-service for user={} in tenant={} (ver={})",
                    userId, tenantId, version);
        } catch (Exception ex) {
            log.warn("Failed to process member permissions sync event in driver-service: {}", ex.getMessage());
        }
    }

    public static Set<String> extractExplicitPermissions(Claims claims, UUID tenantId, UUID userId) {
        if (claims == null) {
            return null;
        }
        Number tokenVerNum = claims.get("perm_ver", Number.class);
        long tokenVer = tokenVerNum != null ? tokenVerNum.longValue() : 1L;

        LivePermissionSnapshot live = getLiveSnapshot(tenantId, userId);
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

    /**
     * Determines the set of acceptable permissions (any-of) for a given HTTP method and path
     * in driver-service.
     */
    public static Set<String> requiredPermissionsForEndpoint(String method, String path) {
        if (method == null || path == null) {
            return Set.of();
        }
        String m = method.toUpperCase(Locale.ROOT);

        // 1. Driver self-service endpoints
        if (path.startsWith("/api/drivers/me") || path.startsWith("/api/earnings/me")) {
            return Set.of("driver:self");
        }

        // 2. Fleet / Driver management endpoints
        if (path.startsWith("/api/drivers")) {
            if ("GET".equals(m)) {
                return Set.of("driver:read", "dispatch:read");
            }
            return Set.of("driver:manage");
        }

        // 3. Earnings & Policy endpoints
        if (path.startsWith("/api/earnings")) {
            if ("GET".equals(m)) {
                return Set.of("billing:read", "driver:read", "analytics:read");
            }
            return Set.of("billing:manage", "tenant:settings:manage", "driver:manage");
        }

        // 4. Settlements endpoints
        if (path.startsWith("/api/settlements")) {
            if ("GET".equals(m)) {
                return Set.of("billing:read", "driver:read", "analytics:read");
            }
            if (path.endsWith("/approve") || path.endsWith("/mark-paid")) {
                return Set.of("billing:manage");
            }
            return Set.of("billing:manage", "driver:manage");
        }

        return Set.of();
    }
}
