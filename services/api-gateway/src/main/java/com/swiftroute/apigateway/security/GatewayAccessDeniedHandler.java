package com.swiftroute.apigateway.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Custom 403 Forbidden handler cho Spring Security WebFlux.
 *
 * Dùng khi user đã xác thực (token hợp lệ) nhưng không đủ quyền
 * truy cập resource (ví dụ: CUSTOMER cố gọi API của ADMIN).
 *
 * Hiện tại Gateway chưa thực hiện role-based authorization
 * (việc này để từng downstream service xử lý), nhưng handler này
 * là safety net nếu sau này thêm authorization rule tại Gateway.
 *
 * Format JSON đồng bộ với ErrorResponse của auth-service.
 */
@Component
public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied: insufficient permissions\",\"path\":\"%s\"}",
                exchange.getRequest().getPath().value()
        );
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
