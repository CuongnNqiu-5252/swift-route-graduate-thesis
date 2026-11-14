package com.swiftroute.apigateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import com.swiftroute.apigateway.security.JwtVerifier;

import reactor.core.publisher.Mono;

/**
 * Filter chạy trước mọi request (Order = -2, trước CustomGlobalFilter
 * Order=-1).
 *
 * Trách nhiệm:
 * 1. Kiểm tra request có thuộc public path không → bỏ qua JWT check.
 * 2. Trích xuất Bearer token từ Authorization header.
 * 3. Gọi JwtVerifier để verify và parse claims.
 * 4. Inject X-User-Id, X-System-Role, X-Tenant-Role, X-Tenant-Id vào request
 * để tracing và các legacy reader không cần parse lại (downstream service vẫn verify JWT).
 * 5. Nếu token thiếu / invalid → trả về 401 JSON ngay tại Gateway.
 *
 * Tách riêng filter này khỏi CustomGlobalFilter để đúng Single Responsibility:
 * - JwtAuthFilter: xác thực & inject headers
 * - CustomGlobalFilter: logging & tracing (X-Request-ID)
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    // Danh sách path cho phép không cần JWT — phải đồng bộ với SecurityConfig
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/email/verify",
            "/api/auth/email/resend-public",
            "/api/auth/password/forgot",
            "/api/auth/password/reset",
            "/api/auth/refresh",
            "/api/auth/invitations/accept",
            "/api/auth/invitations/preview",
            "/actuator/health");

    private final JwtVerifier jwtVerifier;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Bỏ qua JWT check cho public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Lấy Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return writeUnauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // Verify và parse JWT
        return jwtVerifier.verify(token)
                .flatMap(claims -> {
                    String clientTenant = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
                    boolean isSystemAdmin = "SYSTEM_ADMIN".equalsIgnoreCase(claims.systemRole());
                    String effectiveTenantId = null;

                    if (claims.tenantId() != null && !claims.tenantId().isBlank()) {
                        // User belongs to a specific tenant
                        if (!isSystemAdmin) {
                            // Only SYSTEM_ADMIN can switch or override tenant context via header
                            if (clientTenant != null && !clientTenant.isBlank() && !clientTenant.equalsIgnoreCase(claims.tenantId())) {
                                log.warn("Cross-tenant attempt by user {}: claims.tenantId={}, requested={}",
                                        claims.userId(), claims.tenantId(), clientTenant);
                                return writeForbiddenResponse(exchange, "Forbidden: Cross-tenant access denied");
                            }
                            effectiveTenantId = claims.tenantId();
                        } else {
                            // SYSTEM_ADMIN can operate in a specified tenant context, else their own
                            if (clientTenant != null && !clientTenant.isBlank()) {
                                if (!isValidUuid(clientTenant)) {
                                    return writeBadRequestResponse(exchange, "Invalid X-Tenant-Id header format");
                                }
                                effectiveTenantId = clientTenant;
                            } else {
                                effectiveTenantId = claims.tenantId();
                            }
                        }
                    } else if (clientTenant != null && !clientTenant.isBlank()) {
                        // User has no tenant claim (e.g. USER or SYSTEM_ADMIN without scoped token); validate UUID
                        if (!isValidUuid(clientTenant)) {
                            return writeBadRequestResponse(exchange, "Invalid X-Tenant-Id header format");
                        }
                        if (!isSystemAdmin) {
                            log.warn("Non-admin user {} with no tenant claim attempted to supply X-Tenant-Id: {}",
                                    claims.userId(), clientTenant);
                            return writeForbiddenResponse(exchange, "Forbidden: Cross-tenant access denied");
                        }
                        effectiveTenantId = clientTenant;
                    }

                    final String finalTenantId = effectiveTenantId;
                    // Inject verified user context into headers for downstream services
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                // Never trust identity headers supplied by a client.
                                headers.remove("X-User-Id");
                                headers.remove("X-System-Role");
                                headers.remove("X-Tenant-Id");
                                headers.remove("X-Tenant-Role");
                                headers.set("X-User-Id", claims.userId());

                                if (claims.systemRole() != null && !claims.systemRole().isBlank()) {
                                    headers.set("X-System-Role", claims.systemRole());
                                }
                                if (claims.tenantRole() != null && !claims.tenantRole().isBlank()) {
                                    headers.set("X-Tenant-Role", claims.tenantRole());
                                }
                                if (finalTenantId != null) {
                                    headers.set("X-Tenant-Id", finalTenantId);
                                }
                            })
                            .build();

                    log.debug("JWT verified for user: {}, systemRole: {}, tenantRole: {}, tenant: {}",
                            claims.userId(), claims.systemRole(), claims.tenantRole(), finalTenantId);
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(e -> {
                    log.warn("JWT verification failed for path {}: {}", path, e.getMessage());
                    return writeUnauthorizedResponse(exchange, "Invalid or expired token");
                });
    }

    private boolean isValidUuid(String val) {
        try {
            java.util.UUID.fromString(val);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Mono<Void> writeForbiddenResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"%s\",\"path\":\"%s\"}",
                message,
                exchange.getRequest().getPath().value());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private Mono<Void> writeBadRequestResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"%s\",\"path\":\"%s\"}",
                message,
                exchange.getRequest().getPath().value());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * Trả về 401 JSON thay vì HTML mặc định của Spring Security.
     * Format đồng bộ với ErrorResponse của auth-service.
     */
    private Mono<Void> writeUnauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"path\":\"%s\"}",
                message,
                exchange.getRequest().getPath().value());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        // Phải chạy TRƯỚC CustomGlobalFilter (order=-1) để Principal đã được set
        return -2;
    }
}
