package com.swiftroute.driverservice.security;

import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {
    private final RestClient auth;
    @Value("${tenant.internal-key:}") private String key;
    public GatewayHeaderAuthFilter(@Value("${auth-service.url:http://localhost:8081}") String url) {
        var factory=new JdkClientHttpRequestFactory();factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        auth=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI();
        if(!path.startsWith("/api/") || path.matches("/api/drivers/[^/]+/public") || path.matches("/api/drivers/user/[^/]+/public") || request.getMethod().equals("OPTIONS")) {chain.doFilter(request,response);return;}
        String supplied=request.getHeader("X-Service-Key");
        if("GET".equals(request.getMethod()) && (path.equals("/api/drivers") || path.matches("/api/drivers/tenant/[^/]+/available")) && supplied!=null && !key.isBlank()
            && java.security.MessageDigest.isEqual(supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8),key.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {chain.doFilter(request,response);return;}
        String authorization=request.getHeader("Authorization");
        if(authorization==null || !authorization.startsWith("Bearer ")){response.sendError(401);return;}
        String tenant=request.getHeader("X-Tenant-Id");
        String target=tenant;
        if(target==null) target=request.getParameter("tenant_id");
        try {
            if(target!=null) UUID.fromString(target);
            var body=auth.get().uri("/api/auth/context"+(target==null?"":"?tenantId="+target)).header("Authorization",authorization)
                .retrieve().body(new ParameterizedTypeReference<Map<String,Object>>(){});
            var context=(Map<String,Object>)body.get("data");
            boolean admin=Set.of("SYSTEM_ADMIN","ADMIN").contains(context.get("system_role"));
            if(!admin && target==null && !path.startsWith("/api/drivers/me")){response.sendError(403);return;}
            if(!context.get("user_id").toString().equals(request.getHeader("X-User-Id"))){response.sendError(403);return;}
            String role=admin?context.get("system_role").toString():switch(String.valueOf(context.get("membership_role"))) {
                case "OWNER" -> "OWNER";case "ADMIN" -> "TENANT_ADMIN";case "OPERATOR" -> "OPS_STAFF";case "DRIVER" -> "DRIVER";case "VIEWER" -> "PARTNER";default -> context.get("system_role").toString();
            };
            if(!role.equals(request.getHeader("X-User-Role"))){response.sendError(403);return;}
            request.setAttribute("authorizedTenantId",target==null?null:UUID.fromString(target));
            request.setAttribute("systemAdmin",admin);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context.get("user_id").toString(),null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
            if(!admin && role.equals("PARTNER") && !request.getMethod().equals("GET")){response.sendError(403);return;}
        } catch(HttpClientErrorException ex){response.sendError(ex.getStatusCode().value());return;}
        catch(RestClientException ex){response.sendError(503);return;}
        catch(IllegalArgumentException ex){response.sendError(400);return;}
        chain.doFilter(request,response);
    }
    public static void assertTenant(UUID id) {
        var attrs=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(attrs instanceof org.springframework.web.context.request.ServletRequestAttributes a) {
            var request=a.getRequest();if(Boolean.TRUE.equals(request.getAttribute("systemAdmin")))return;
            UUID tenant=(UUID)request.getAttribute("authorizedTenantId");
            if(tenant!=null && !tenant.equals(id))throw new com.swiftroute.driverservice.exception.AppException(com.swiftroute.driverservice.exception.ErrorCode.ACCESS_DENIED);
        }
    }
}
