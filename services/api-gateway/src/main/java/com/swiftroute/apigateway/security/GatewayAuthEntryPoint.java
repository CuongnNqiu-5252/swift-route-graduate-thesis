package com.swiftroute.apigateway.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Custom 401 Unauthorized handler cho Spring Security WebFlux.
 *
 * Lý do cần class này:
 *  - Mặc định Spring Security trả về HTML redirect đến login page khi gặp 401.
 *  - Với REST API / mobile client, cần trả về JSON để client xử lý được.
 *  - Format JSON đồng bộ với ErrorResponse của auth-service.
 *
 * Được đăng ký trong SecurityConfig.exceptionHandling().authenticationEntryPoint().
 *
 * Lưu ý: Trong luồng thực tế, JwtAuthFilter đã bắt 401 trước khi
 * Spring Security layer. Class này là safety net cho các trường hợp
 * Spring Security bắt được lỗi xác thực qua các filter khác.
 */
@Component
public class GatewayAuthEntryPoint implements ServerAuthenticationEntryPoint {

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"path\":\"%s\"}",
                ex.getMessage() != null ? ex.getMessage() : "Authentication required",
                exchange.getRequest().getPath().value()
        );
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
