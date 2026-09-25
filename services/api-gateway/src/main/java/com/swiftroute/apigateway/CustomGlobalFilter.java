package com.swiftroute.apigateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CustomGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("Bắt đầu request: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI());

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            log.info("Kết thúc request với HTTP Status: {}", exchange.getResponse().getStatusCode());
        }));
    }

    @Override
    public int getOrder() {
        // Thứ tự ưu tiên của filter. Số càng nhỏ càng chạy trước.
        return -1;
    }
}
