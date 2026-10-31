package com.swiftroute.driverservice.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads identity headers injected by the API Gateway instead of re-parsing the JWT.
 * The Gateway is the single point of JWT validation; downstream services only
 * need to trust these headers:
 *   - X-User-Id   : UUID of the authenticated user
 *   - X-User-Role : Role string (DRIVER, ADMIN, OPS_STAFF, …)
 */
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String role   = request.getHeader("X-User-Role");

        // If the Gateway injected both headers, populate the SecurityContext so that
        // @PreAuthorize / hasRole() annotations work without touching the JWT.
        if (userId != null && !userId.isBlank() && role != null && !role.isBlank()) {
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
