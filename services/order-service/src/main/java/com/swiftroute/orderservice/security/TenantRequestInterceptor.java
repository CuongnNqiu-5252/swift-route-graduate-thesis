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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if ("OPTIONS".equals(request.getMethod()))
            return true;
        String auth = request.getHeader("Authorization");
        var claims = tokens.parseAndValidate(auth);
        if (claims == null || claims.getSubject() == null || claims.getSubject().isBlank())
            throw new AppException(ErrorCode.UNAUTHENTICATED);

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

        boolean tenantApi = path.startsWith("/api/tenants") || path.startsWith("/api/v1/tenants");
        if (tenantApi) {
            return true;
        }

        String requestedTenant = request.getHeader("X-Tenant-Id");
        if (requestedTenant == null || requestedTenant.isBlank()) {
            requestedTenant = request.getParameter("tenant_id");
        }
        UUID parsedRequestedTenant = null;
        if (requestedTenant != null && !requestedTenant.isBlank()) {
            try {
                parsedRequestedTenant = UUID.fromString(requestedTenant.trim());
            } catch (IllegalArgumentException ex) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
        }

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

        if (!"GET".equals(request.getMethod()) && "VIEWER".equalsIgnoreCase(tokenTenantRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        request.setAttribute("authorizedTenantId", jwtTenantId);
        request.setAttribute("effectiveRole", tokenTenantRole.toUpperCase(Locale.ROOT));
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
