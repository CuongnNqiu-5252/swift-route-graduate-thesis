package com.swiftroute.apigateway.filter;

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
import reactor.core.publisher.Mono;

import com.swiftroute.apigateway.security.JwtVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Filter chạy trước mọi request (Order = -2, trước CustomGlobalFilter Order=-1).
 *
 * Trách nhiệm:
 *  1. Kiểm tra request có thuộc public path không → bỏ qua JWT check.
 *  2. Trích xuất Bearer token từ Authorization header.
 *  3. Gọi JwtVerifier để verify và parse claims.
 *  4. Inject X-User-Id, X-User-Role, X-Tenant-Id vào request
 *     để các downstream service không cần parse JWT lại.
 *  5. Nếu token thiếu / invalid → trả về 401 JSON ngay tại Gateway.
 *
 * Tách riêng filter này khỏi CustomGlobalFilter để đúng Single Responsibility:
 *  - JwtAuthFilter: xác thực & inject headers
 *  - CustomGlobalFilter: logging & tracing (X-Request-ID)
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    // Danh sách path cho phép không cần JWT — phải đồng bộ với SecurityConfig
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/actuator/health"
    );

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
                    // Inject user context vào header cho downstream services
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                // Never trust identity headers supplied by a client.
                                headers.remove("X-User-Id");
                                headers.remove("X-User-Role");
                                headers.remove("X-Tenant-Id");
                                headers.set("X-User-Id", claims.userId());
                                headers.set("X-User-Role", claims.role());
                                headers.set("X-Tenant-Id", claims.tenantId() != null ? claims.tenantId() : "");
                            })
                            // Xoá Authorization header để downstream không nhận raw JWT
                            // (tùy chính sách bảo mật — hiện tại giữ lại để tương thích)
                            .build();

                    log.debug("JWT verified for user: {}, role: {}", claims.userId(), claims.role());
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(e -> {
                    log.warn("JWT verification failed for path {}: {}", path, e.getMessage());
                    return writeUnauthorizedResponse(exchange, "Invalid or expired token");
                });
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
                exchange.getRequest().getPath().value()
        );
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(bytes))
        );
    }

    @Override
    public int getOrder() {
        // Phải chạy TRƯỚC CustomGlobalFilter (order=-1) để Principal đã được set
        return -2;
    }
}
