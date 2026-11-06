package com.swiftroute.orderservice.security;

import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import com.swiftroute.orderservice.exception.*;
import lombok.RequiredArgsConstructor;

/** Validates live membership as well as signed identity before legacy header-based controllers. */
@Component
@RequiredArgsConstructor
public class TenantRequestInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final JwtTokenValidator tokens;
    private final TenantIdentityClient identities;
    @org.springframework.beans.factory.annotation.Value("${tenant.internal-key:}") private String serviceKey;
    private final com.swiftroute.orderservice.repository.TenantRepository tenants;
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(this).addPathPatterns("/api/**"); }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        String path=request.getRequestURI();
        if ("OPTIONS".equals(request.getMethod()) || path.startsWith("/api/orders/track/")) return true;
        if ("GET".equals(request.getMethod()) && path.matches("/api/tenants/[0-9a-fA-F-]{36}/dispatch-config")) {
            String key=request.getHeader("X-Service-Key");
            if (key!=null && !serviceKey.isBlank() && java.security.MessageDigest.isEqual(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),serviceKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                UUID id=UUID.fromString(path.split("/")[3]);
                var tenant=tenants.findById(id).orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
                com.swiftroute.orderservice.service.OperationalReadinessService.requireApproved(tenant);return true;
            }
        }
        String auth=request.getHeader("Authorization"); var claims=tokens.parseAndValidate(auth);
        if(claims==null)throw new AppException(ErrorCode.UNAUTHENTICATED);
        String headerUser=request.getHeader("X-User-Id"),headerRole=request.getHeader("X-User-Role");
        if(headerUser!=null && !headerUser.equals(claims.getSubject()))throw new AppException(ErrorCode.ACCESS_DENIED);
        if(headerRole!=null && !headerRole.equals(claims.get("_ROLE",String.class)))throw new AppException(ErrorCode.ACCESS_DENIED);
        String tenant=request.getHeader("X-Tenant-Id");if(tenant==null)tenant=claims.get("tenant_id",String.class);
        boolean tenantApi=path.startsWith("/api/tenants") || path.startsWith("/api/v1/tenants");
        if(tenantApi) {
            if(path.matches("/api/(v1/)?tenants/[0-9a-fA-F-]{36}/provisioning(/retry)?")) return true;
            // New workflow controller verifies requested path tenants. Protect retained dispatch/config routes here.
            String[] parts=path.split("/");
            for(int i=0;i<parts.length-1;i++) if("tenants".equals(parts[i])) {
                try {
                    UUID id=UUID.fromString(parts[i+1]);var context=identities.context(auth,id);
                    if(!"GET".equals(request.getMethod()) && path.endsWith("dispatch-config")) identities.requireOwner(context);
                } catch(IllegalArgumentException ignored) { }
            }
            return true;
        }
        if(headerUser==null || headerRole==null)throw new AppException(ErrorCode.UNAUTHENTICATED);
        if(request.getHeader("X-Tenant-Id")==null && request.getParameter("tenant_id")==null)throw new AppException(ErrorCode.ACCESS_DENIED);
        if (tenant==null || tenant.isBlank()) throw new AppException(ErrorCode.ACCESS_DENIED);
        UUID id;
        try { id=UUID.fromString(tenant); } catch(IllegalArgumentException ex) {throw new AppException(ErrorCode.VALIDATION_ERROR);}
        var context=identities.context(auth,id);
        request.setAttribute("authorizedTenantId",id);
        if(!"GET".equals(request.getMethod()) && !TenantIdentityClient.systemAdmin(context)
            && "VIEWER".equals(context.get("membership_role")))throw new AppException(ErrorCode.ACCESS_DENIED);
        return true;
    }
    public static void assertRequestTenant(UUID tenantId) {
        var attrs=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            UUID current=(UUID)servlet.getRequest().getAttribute("authorizedTenantId");
            if(current!=null && !current.equals(tenantId)) throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
