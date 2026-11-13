package com.swiftroute.driverservice.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import com.swiftroute.driverservice.exception.AppException;
import com.swiftroute.driverservice.exception.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless JWT authentication filter for driver-service.
 * Verifies the Bearer token signature locally and enforces tenant scoping
 * using system_role, tenant_id, and tenant_role claims.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtParser jwtParser;
    private final String internalServiceKey;

    @Autowired
    public JwtAuthenticationFilter(
            @Value("${jwt.secret:your_super_secret_key_change_in_production}") String jwtSecret,
            @Value("${tenant.internal-key:}") String internalServiceKey) {
        this(buildJwtParser(jwtSecret), internalServiceKey);
    }

    JwtAuthenticationFilter(JwtParser jwtParser, String internalServiceKey) {
        this.jwtParser = jwtParser;
        this.internalServiceKey = internalServiceKey == null ? "" : internalServiceKey;
    }

    static JwtParser buildJwtParser(String jwtSecret) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || "OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String supplied = request.getHeader("X-Service-Key");
        if ("GET".equals(request.getMethod())
                && path.equals("/api/drivers")
                && supplied != null && !internalServiceKey.isBlank()
                && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                        internalServiceKey.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Claims claims;
        try {
            claims = jwtParser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        UUID parsedUserId;
        try {
            parsedUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String systemRole = claims.get("system_role", String.class);
        String tokenTenantId = claims.get("tenant_id", String.class);
        String tokenTenantRole = claims.get("tenant_role", String.class);

        String target = request.getHeader("X-Tenant-Id");
        if (target == null || target.isBlank()) {
            target = request.getParameter("tenant_id");
        }
        UUID parsedTargetTenant = null;
        if (target != null && !target.isBlank()) {
            try {
                parsedTargetTenant = UUID.fromString(target);
            } catch (IllegalArgumentException ex) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        }

        boolean admin = "SYSTEM_ADMIN".equalsIgnoreCase(systemRole);
        UUID authorizedTenantId = parsedTargetTenant;

        if (!admin) {
            if (tokenTenantId == null || tokenTenantId.isBlank()
                    || tokenTenantRole == null || tokenTenantRole.isBlank()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            UUID jwtTenantId;
            try {
                jwtTenantId = UUID.fromString(tokenTenantId);
            } catch (IllegalArgumentException ex) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            if (parsedTargetTenant != null && !parsedTargetTenant.equals(jwtTenantId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            authorizedTenantId = jwtTenantId;
        }

        String role = admin ? "SYSTEM_ADMIN" : tokenTenantRole.toUpperCase(Locale.ROOT);
        if (!admin && "VIEWER".equals(role) && !"GET".equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        if (admin && tokenTenantRole != null && !tokenTenantRole.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + tokenTenantRole.toUpperCase(Locale.ROOT)));
        }

        request.setAttribute("authorizedUserId", parsedUserId);
        request.setAttribute("authorizedTenantId", authorizedTenantId);
        request.setAttribute("systemAdmin", admin);
        request.setAttribute("effectiveRole", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, authorities));

        chain.doFilter(request, response);
    }

    public static void assertTenant(UUID id) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes a) {
            var request = a.getRequest();
            if (Boolean.TRUE.equals(request.getAttribute("systemAdmin"))) {
                return;
            }
            UUID tenant = (UUID) request.getAttribute("authorizedTenantId");
            if (tenant != null && !tenant.equals(id)) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
        }
    }
}

