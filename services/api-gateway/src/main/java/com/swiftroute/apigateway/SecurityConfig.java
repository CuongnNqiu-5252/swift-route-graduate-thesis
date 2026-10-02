package com.swiftroute.apigateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.swiftroute.apigateway.security.GatewayAccessDeniedHandler;
import com.swiftroute.apigateway.security.GatewayAuthEntryPoint;

import java.util.List;

/**
 * Security configuration cho Spring Cloud Gateway (WebFlux).
 *
 * Chiến lược phân layer bảo mật:
 *  - Tầng này (Spring Security): CORS, CSRF disable, public path whitelist
 *  - JwtAuthFilter (GlobalFilter order=-2): JWT verify + header injection
 *  - Downstream service: role-based authorization theo nghiệp vụ từng service
 *
 * Lý do Gateway KHÔNG làm role-based authorization:
 *  - Mỗi service có thể có route authorization rule riêng (ví dụ: DRIVER chỉ
 *    truy cập /api/driver/**, ADMIN truy cập /api/admin/**)
 *  - Để tránh phải update Gateway mỗi khi thêm rule mới ở service con
 *  - Gateway chỉ đảm bảo token hợp lệ, downstream service kiểm tra quyền cụ thể
 *
 * CORS:
 *  - Cho phép React web-admin (localhost:3000) và Flutter Web (localhost:4200)
 *  - Trong production, thay bằng domain thật qua biến môi trường
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final GatewayAuthEntryPoint authEntryPoint;
    private final GatewayAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(GatewayAuthEntryPoint authEntryPoint,
                          GatewayAccessDeniedHandler accessDeniedHandler) {
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange
                        // Public endpoints — phải đồng bộ với JwtAuthFilter.PUBLIC_PATHS
                        .pathMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh"
                        ).permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight
                        .pathMatchers("/actuator/health").permitAll()
                        // Tất cả còn lại yêu cầu authenticated
                        // (JWT verification thực sự do JwtAuthFilter xử lý)
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .build();
    }

    /**
     * CORS configuration cho phép React web-admin và Flutter web gọi API.
     *
     * Các header được expose (Access-Control-Expose-Headers):
     *  - X-Request-ID: để client tracking request
     *
     * Lưu ý: Không expose X-User-* headers ra ngoài — chỉ dùng nội bộ
     * giữa Gateway và downstream services.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allowed origins: React web-admin, Flutter web dev server
        // Trong production: set qua biến môi trường CORS_ALLOWED_ORIGINS
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",  // React web-admin
                "http://localhost:4200"   // Flutter web (nếu dùng)
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Request-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache preflight response 1 giờ

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
