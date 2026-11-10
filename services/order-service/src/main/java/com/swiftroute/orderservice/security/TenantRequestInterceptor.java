package com.swiftroute.orderservice.security;

import java.util.Set;
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
 * Validates live membership as well as signed identity before legacy
 * header-based controllers.
 */
@Component
@RequiredArgsConstructor
public class TenantRequestInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final JwtTokenValidator tokens;
    private final TenantIdentityClient identities;
    @org.springframework.beans.factory.annotation.Value("${tenant.internal-key:}")
    private String serviceKey;
    private final com.swiftroute.orderservice.repository.TenantRepository tenants;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if ("OPTIONS".equals(request.getMethod()) || path.startsWith("/api/orders/track/"))
            return true;
        String auth = request.getHeader("Authorization");
        var claims = tokens.parseAndValidate(auth);
        if (claims == null)
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        String headerUser = request.getHeader("X-User-Id"), headerRole = request.getHeader("X-User-Role");
        if (headerUser != null && !headerUser.equals(claims.getSubject()))
            throw new AppException(ErrorCode.ACCESS_DENIED);
        if (headerRole != null && !headerRole.equals(claims.get("_ROLE", String.class)))
            throw new AppException(ErrorCode.ACCESS_DENIED);
        String tenant = request.getHeader("X-Tenant-Id");
        if (tenant == null)
            tenant = claims.get("tenant_id", String.class);
        boolean tenantApi = path.startsWith("/api/tenants") || path.startsWith("/api/v1/tenants");
        if (tenantApi) {
            return true;
        }
        if (headerUser == null || headerRole == null)
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (request.getHeader("X-Tenant-Id") == null && request.getParameter("tenant_id") == null)
            throw new AppException(ErrorCode.ACCESS_DENIED);
        if (tenant == null || tenant.isBlank())
            throw new AppException(ErrorCode.ACCESS_DENIED);
        UUID id;
        try {
            id = UUID.fromString(tenant);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        // Fast-path: if JWT claims already specify the matching tenant and role, validate locally without remote HTTP call
        String tokenTenantId = claims.get("tenant_id", String.class);
        String tokenRole = claims.get("_ROLE", String.class);
        String systemRole = claims.get("system_role", String.class);
        boolean isSystemAdmin = systemRole != null && Set.of("SYSTEM_ADMIN", "ADMIN").contains(systemRole);

        if (id.toString().equalsIgnoreCase(tokenTenantId) && tokenRole != null) {
            request.setAttribute("authorizedTenantId", id);
            if (!"GET".equals(request.getMethod()) && !isSystemAdmin && "VIEWER".equalsIgnoreCase(tokenRole)) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            return true;
        }

        var context = identities.context(auth, id);
        request.setAttribute("authorizedTenantId", id);
        if (!"GET".equals(request.getMethod()) && !TenantIdentityClient.systemAdmin(context)
                && "VIEWER".equals(context.get("membership_role")))
            throw new AppException(ErrorCode.ACCESS_DENIED);
        return true;
    }

    public static void assertRequestTenant(UUID tenantId) {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            UUID current = (UUID) servlet.getRequest().getAttribute("authorizedTenantId");
            if (current != null && !current.equals(tenantId))
                throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
