package com.swiftroute.apigateway.filter;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Edge rate-limiting filter on POST /api/auth/driver/activate to protect the 12-digit
 * activation endpoint against brute-force attempts before forwarding to auth-service.
 */
@Component
public class ActivationRateLimitFilter implements GlobalFilter, Ordered {

    private static final String ACTIVATE_PATH = "/api/auth/driver/activate";
    private static final int MAX_REQUESTS_PER_MINUTE = 12;
    private static final long WINDOW_MS = 60_000L;

    private record WindowCounter(long windowStartMs, AtomicInteger count) {
    }

    private final Map<String, WindowCounter> ipCounters = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST
                || !ACTIVATE_PATH.equals(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        String clientIp = extractClientIp(exchange);
        long now = System.currentTimeMillis();
        if (ipCounters.size() > 5000) {
            ipCounters.entrySet().removeIf(entry -> now - entry.getValue().windowStartMs() > WINDOW_MS * 2);
        }

        WindowCounter counter = ipCounters.compute(clientIp, (k, existing) -> {
            if (existing == null || now - existing.windowStartMs() >= WINDOW_MS) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (counter.count().get() > MAX_REQUESTS_PER_MINUTE) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"data\":null,\"error\":{\"code\":429,\"message\":\"Quá nhiều lần thử kích hoạt. Vui lòng thử lại sau ít phút.\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        }

        return chain.filter(exchange);
    }

    private String extractClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return -3;
    }
}
