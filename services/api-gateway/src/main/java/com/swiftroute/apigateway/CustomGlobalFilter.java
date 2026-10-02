package com.swiftroute.apigateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter chịu trách nhiệm logging và request tracing.
 *
 * Trách nhiệm (sau khi tách JWT logic sang JwtAuthFilter):
 *  1. Gán X-Request-ID cho mỗi request (correlation ID để trace log xuyên service)
 *  2. Log thông tin request: method, URI, user context (nếu có)
 *  3. Log HTTP status sau khi request hoàn thành
 *
 * Thứ tự thực thi:
 *  - JwtAuthFilter (order = -2): verify JWT, inject X-User-Id/X-User-Role/X-Tenant-Id
 *  - CustomGlobalFilter (order = -1): log + gán X-Request-ID (đọc được header đã inject)
 *
 * Lý do đọc X-User-Id từ header thay vì từ Principal:
 *  - Principal trong WebFlux cần SecurityContext (async lookup)
 *  - Header đã được JwtAuthFilter inject → đọc trực tiếp, đơn giản hơn
 */
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CustomGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        // Đọc user context đã được JwtAuthFilter inject (nếu có)
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String userRole = exchange.getRequest().getHeaders().getFirst("X-User-Role");

        log.info("[{}] {} {} | user={} role={}",
                requestId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI(),
                userId != null ? userId : "anonymous",
                userRole != null ? userRole : "-"
        );

        // Thêm X-Request-ID vào request để downstream service ghi vào log của mình
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header("X-Request-ID", requestId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[{}] Completed {} | status={} | {}ms",
                            requestId,
                            exchange.getRequest().getURI().getPath(),
                            exchange.getResponse().getStatusCode(),
                            duration
                    );
                }));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
