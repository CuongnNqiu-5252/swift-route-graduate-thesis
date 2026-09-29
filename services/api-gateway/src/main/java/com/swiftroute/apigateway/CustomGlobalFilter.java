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

@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CustomGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("Bắt đầu request: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI());

        String requestId = UUID.randomUUID().toString();

        return exchange.getPrincipal()
            .flatMap(principal -> {
                log.info("Principal: {}", principal.getName());

                ServerHttpRequest mutatedRequest = exchange.getRequest()
                        .mutate()
                        .header("X-Request-ID", requestId)
                        .header("X-User-Id", principal.getName() != null ? principal.getName() : "")
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            })
            .switchIfEmpty(Mono.defer(() -> {
                // Xử lý request không có Principal (chưa đăng nhập / Public API)
                ServerHttpRequest mutatedRequest = exchange.getRequest()
                        .mutate()
                        .header("X-Request-ID", requestId)
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }))
            .then(Mono.fromRunnable(() -> {
                log.info("Kết thúc request với HTTP Status: {}", exchange.getResponse().getStatusCode());
            }));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
