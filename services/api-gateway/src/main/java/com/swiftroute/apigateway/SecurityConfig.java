package com.swiftroute.apigateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

        /**
         * No-op ReactiveUserDetailsService để ngăn Spring Boot tạo default security
         * password.
         * Gateway không cần user authentication — JWT được validate riêng.
         */
        // @Bean
        // public ReactiveUserDetailsService reactiveUserDetailsService() {
        // return username -> Mono.empty();
        // }

        @Bean
        public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
                return http
                                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                                .authorizeExchange(exchange -> exchange
                                                .pathMatchers(
                                                                "/api/auth/login",
                                                                "/api/auth/register",
                                                                "/api/auth/refresh")
                                                .permitAll()
                                                .pathMatchers("/actuator/health").permitAll()
                                                .anyExchange().permitAll())
                                .build();
        }
}
