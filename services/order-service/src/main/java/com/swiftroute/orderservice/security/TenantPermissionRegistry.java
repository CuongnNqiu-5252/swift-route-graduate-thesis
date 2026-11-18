package com.swiftroute.orderservice.security;

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
 * ("tenant.member_permissions_updated.v1") and maps order-service HTTP
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    @RabbitListener(queues = "order.member-permissions-sync")
    public void onPermissionsUpdated(Message message) {
        try {
            JsonNode node = MAPPER.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
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
            log.info("Synchronized live permissions in order-service for user={} in tenant={} (ver={})",
                    userId, tenantId, version);
        } catch (Exception ex) {
            log.warn("Failed to process member permissions sync event in order-service: {}", ex.getMessage());
        }
    }

    /**
     * Extracts explicit permissions from JWT claims or returns null if the token
     * does not carry a "permissions" claim (e.g. legacy/synthetic test tokens).
     */
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
     * in order-service. Returns an empty set if no granular permission check applies.
     */
    public static Set<String> requiredPermissionsForEndpoint(String method, String path) {
        if (method == null || path == null) {
            return Set.of();
        }
        String m = method.toUpperCase(Locale.ROOT);

        // 1. Pickup Locations (Warehouses / Hubs)
        if (path.startsWith("/api/pickup-locations")) {
            if ("GET".equals(m)) {
                return Set.of("hub:read", "order:read", "order:create");
            }
            return Set.of("hub:manage");
        }

        // 2. Tenant Settings & Delivery Zones
        if (path.contains("/me/settings") || path.contains("/me/delivery-zone")) {
            if ("GET".equals(m) || path.endsWith("/check")) {
                return Set.of("tenant:settings:read", "order:read", "order:create");
            }
            return Set.of("tenant:settings:manage");
        }

        // 3. Dispatch & Route Optimization (open to dispatch:read / dispatch:execute, which DRIVER also holds)
        if (path.startsWith("/api/orders/dispatch")) {
            if ("GET".equals(m)) {
                return Set.of("dispatch:read", "order:read");
            }
            if (path.endsWith("/acknowledge") || path.endsWith("/decline")) {
                return Set.of("order:deliver", "dispatch:execute");
            }
            return Set.of("dispatch:execute");
        }

        if (path.equals("/api/orders/assign-batch")) {
            return Set.of("dispatch:execute", "order:update");
        }

        if (path.startsWith("/api/orders/operation-flow/drafts/")) {
            return Set.of(path.equals("/api/orders/operation-flow/drafts/planning")
                    || path.startsWith("/api/orders/operation-flow/drafts/planning/")
                    ? "dispatch:execute" : "order:create");
        }

        if (path.equals("/api/orders/import")
                || path.equals("/api/orders/import/pending")
                || path.equals("/api/orders/import/pending/complete")
                || path.equals("/api/orders/import/pending/cancel")) {
            return Set.of("order:create");
        }

        // 4. Orders
        if (path.startsWith("/api/orders")) {
            if ("GET".equals(m)) {
                return Set.of("order:read");
            }
            if ("POST".equals(m) && (path.equals("/api/orders") || path.equals("/api/orders/"))) {
                return Set.of("order:create");
            }
            if (path.endsWith("/cancel")) {
                return Set.of("order:cancel");
            }
            if (path.endsWith("/incident") || path.endsWith("/incidents")) {
                return Set.of("order:deliver", "order:update");
            }
            if (path.contains("/tracking/simulate")) {
                return Set.of("dispatch:execute", "order:update", "order:deliver");
            }
            return Set.of("order:update");
        }

        // 5. Delivery Tasks
        if (path.startsWith("/api/delivery-tasks")) {
            if ("GET".equals(m)) {
                return Set.of("order:read", "order:deliver");
            }
            return Set.of("order:deliver", "order:update");
        }

        // 6. Operations & Exceptions (Incidents & Returns)
        if (path.startsWith("/api/operations")) {
            if ("GET".equals(m)) {
                return Set.of("order:read", "sla:read");
            }
            if (path.equals("/api/operations/incidents")
                    || path.endsWith("/incidents")
                    || path.endsWith("/arrive")
                    || path.endsWith("/handover")) {
                return Set.of("order:deliver", "order:update", "sla:manage");
            }
            return Set.of("order:update", "hub:manage", "sla:manage");
        }

        // 7. Package Custody
        if (path.startsWith("/api/custody")) {
            if ("GET".equals(m)) {
                return Set.of("order:read", "hub:read", "order:deliver");
            }
            return Set.of("order:deliver", "hub:manage", "order:update");
        }

        // 8. COD Ledger
        if (path.startsWith("/api/cod")) {
            if (path.equals("/api/cod/mine/custody")
                    || path.endsWith("/collections")
                    || path.equals("/api/cod/handovers")) {
                if ("GET".equals(m)) {
                    return Set.of("order:deliver", "billing:read", "order:read");
                }
                return Set.of("order:deliver", "billing:manage", "order:update");
            }
            if ("GET".equals(m)) {
                return Set.of("billing:read", "order:read");
            }
            return Set.of("billing:manage", "order:update", "hub:manage");
        }

        return Set.of();
    }
}
