package com.swiftroute.orderservice.security;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Enforces stateless JWT signature verification and strict tenant scoping
 * from verified JWT claims before controllers execute.
 */
@Component
@RequiredArgsConstructor
public class TenantRequestInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final JwtTokenValidator tokens;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private boolean isSessionRevoked(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        if (TenantPermissionRegistry.isSessionRevoked(sessionId)) {
            return true;
        }
        if (redisTemplate != null) {
            try {
                if (Boolean.TRUE.equals(redisTemplate.hasKey("auth:revoked-session:" + sessionId))) {
                    TenantPermissionRegistry.recordRevokedSession(
                            sessionId,
                            (System.currentTimeMillis() / 1000L) + 3600L);
                    return true;
                }
            } catch (Exception ignored) {
                // Fall back to RabbitMQ-synchronized cache if Redis is transiently unreachable
            }
        }
        return false;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/tracking/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if ("OPTIONS".equals(request.getMethod()))
            return true;
        if ("GET".equalsIgnoreCase(request.getMethod()) && path != null && path.startsWith("/api/public/tracking/"))
            return true;
        String auth = request.getHeader("Authorization");
        var claims = tokens.parseAndValidate(auth);
        if (claims == null || claims.getSubject() == null || claims.getSubject().isBlank())
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        if (claims.containsKey("sid")) {
            String sidClaim = claims.get("sid", String.class);
            if (sidClaim != null && !sidClaim.isBlank()) {
                try {
                    if (isSessionRevoked(UUID.fromString(sidClaim.trim()))) {
                        throw new AppException(ErrorCode.UNAUTHENTICATED);
                    }
                } catch (IllegalArgumentException ex) {
                    throw new AppException(ErrorCode.UNAUTHENTICATED);
                }
            }
        }

        UUID userId;
        try {
            userId = UUID.fromString(claims.getSubject().trim());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String systemRole = claims.get("system_role", String.class);
        String tokenTenantId = claims.get("tenant_id", String.class);
        String tokenTenantRole = claims.get("tenant_role", String.class);
        boolean isSystemAdmin = "SYSTEM_ADMIN".equalsIgnoreCase(systemRole);
        String initialRole = isSystemAdmin
                ? "SYSTEM_ADMIN"
                : (tokenTenantRole != null && !tokenTenantRole.isBlank()
                        ? tokenTenantRole.toUpperCase(Locale.ROOT)
                        : (systemRole != null && !systemRole.isBlank() ? systemRole.toUpperCase(Locale.ROOT) : "USER"));

        request.setAttribute("authorizedUserId", userId);
        request.setAttribute("effectiveRole", initialRole);

        boolean isRegistration = "POST".equalsIgnoreCase(request.getMethod())
                && (path.equals("/api/tenants") || path.equals("/api/tenants/")
                        || path.equals("/api/v1/tenants") || path.equals("/api/v1/tenants/"));

        boolean isProvisioning = (path.startsWith("/api/tenants/") || path.startsWith("/api/v1/tenants/"))
                && (path.endsWith("/provisioning") || path.endsWith("/provisioning/")
                        || path.endsWith("/provisioning/retry") || path.endsWith("/provisioning/retry/"))
                && ("GET".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod()));

        if (isRegistration || isProvisioning) {
            return true;
        }

        UUID pathTenantId = extractPathTenantId(path);

        String requestedTenant = request.getHeader("X-Tenant-Id");
        if (requestedTenant == null || requestedTenant.isBlank()) {
            requestedTenant = request.getParameter("tenant_id");
        }
        UUID parsedHeaderTenant = null;
        if (requestedTenant != null && !requestedTenant.isBlank()) {
            try {
                parsedHeaderTenant = UUID.fromString(requestedTenant.trim());
            } catch (IllegalArgumentException ex) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
        }

        if (pathTenantId != null && parsedHeaderTenant != null && !pathTenantId.equals(parsedHeaderTenant)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        UUID parsedRequestedTenant = pathTenantId != null ? pathTenantId : parsedHeaderTenant;

        if (isSystemAdmin) {
            UUID adminTenant = parsedRequestedTenant;
            if (adminTenant == null && tokenTenantId != null && !tokenTenantId.isBlank()) {
                try {
                    adminTenant = UUID.fromString(tokenTenantId.trim());
                } catch (IllegalArgumentException ex) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR);
                }
            }
            request.setAttribute("authorizedTenantId", adminTenant);
            request.setAttribute("effectiveRole", "SYSTEM_ADMIN");
            return true;
        }

        // Stateless tenant-scoped check: non-admin callers must hold a JWT scoped to a
        // valid tenant
        if (tokenTenantId == null || tokenTenantId.isBlank()
                || tokenTenantRole == null || tokenTenantRole.isBlank()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        UUID jwtTenantId;
        try {
            jwtTenantId = UUID.fromString(tokenTenantId.trim());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (parsedRequestedTenant != null && !parsedRequestedTenant.equals(jwtTenantId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        String effectiveTenantRole = tokenTenantRole.toUpperCase(Locale.ROOT);
        Number tokenPermVerNum = claims.get("perm_ver", Number.class);
        long tokenPermVer = tokenPermVerNum != null ? tokenPermVerNum.longValue() : 1L;
        TenantPermissionRegistry.LivePermissionSnapshot liveSnapshot = TenantPermissionRegistry
                .getLiveSnapshot(jwtTenantId, userId);
        if (liveSnapshot != null && liveSnapshot.permissionsVersion() > tokenPermVer) {
            if (!"ACTIVE".equalsIgnoreCase(liveSnapshot.status())) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            effectiveTenantRole = liveSnapshot.membershipRole();
        }

        java.util.Set<String> explicitPermissions = TenantPermissionRegistry
                .extractExplicitPermissions(claims, jwtTenantId, userId);
        if (explicitPermissions != null) {
            request.setAttribute("authorizedPermissions", explicitPermissions);
        }

        if (!"OWNER".equalsIgnoreCase(effectiveTenantRole)) {
            java.util.Set<String> requiredPerms = TenantPermissionRegistry
                    .requiredPermissionsForEndpoint(request.getMethod(), path);
            if (explicitPermissions != null && !requiredPerms.isEmpty()) {
                if (java.util.Collections.disjoint(explicitPermissions, requiredPerms)) {
                    throw new AppException(ErrorCode.ACCESS_DENIED);
                }
            } else if (!"GET".equals(request.getMethod()) && "VIEWER".equalsIgnoreCase(effectiveTenantRole)) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
        }

        request.setAttribute("authorizedTenantId", jwtTenantId);
        request.setAttribute("effectiveRole", effectiveTenantRole);
        return true;
    }

    private UUID extractPathTenantId(String path) {
        String prefix = null;
        if (path.startsWith("/api/tenants/")) {
            prefix = "/api/tenants/";
        } else if (path.startsWith("/api/v1/tenants/")) {
            prefix = "/api/v1/tenants/";
        }
        if (prefix == null) {
            return null;
        }
        String remainder = path.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        String segment = slashIndex >= 0 ? remainder.substring(0, slashIndex) : remainder;
        try {
            return UUID.fromString(segment.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void assertRequestTenant(UUID tenantId) {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            UUID current = (UUID) servlet.getRequest().getAttribute("authorizedTenantId");
            if (current != null && !current.equals(tenantId))
                throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean isAuthorizedForPermission(
            String effectiveRole,
            String requiredPermission,
            java.util.Set<String> fallbackAllowedRoles) {
        if ("SYSTEM_ADMIN".equalsIgnoreCase(effectiveRole) || "OWNER".equalsIgnoreCase(effectiveRole)) {
            return true;
        }
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            Object rawPerms = servlet.getRequest().getAttribute("authorizedPermissions");
            if (rawPerms instanceof java.util.Set<?> permSet) {
                return permSet.contains(requiredPermission);
            }
        }
        return effectiveRole != null
                && fallbackAllowedRoles != null
                && fallbackAllowedRoles.contains(effectiveRole.toUpperCase(Locale.ROOT));
    }
}
