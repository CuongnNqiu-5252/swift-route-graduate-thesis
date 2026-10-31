package com.swiftroute.driverservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.swiftroute.driverservice.security.GatewayHeaderAuthFilter;

/**
 * Security configuration for driver-service.
 *
 * Authentication is delegated entirely to the API Gateway — it validates the
 * JWT and injects X-User-Id / X-User-Role into every forwarded request.
 * This service only reads those headers via GatewayHeaderAuthFilter.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize("hasRole('DRIVER')")
public class SecurityConfig {

    private final GatewayHeaderAuthFilter gatewayHeaderAuthFilter;

    public SecurityConfig(GatewayHeaderAuthFilter gatewayHeaderAuthFilter) {
        this.gatewayHeaderAuthFilter = gatewayHeaderAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/api/drivers/*/public")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
