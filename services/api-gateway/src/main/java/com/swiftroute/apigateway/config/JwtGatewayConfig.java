package com.swiftroute.apigateway.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Cấu hình JWT tại API Gateway.
 *
 * Dùng cùng JWT_SECRET với auth-service (đọc qua biến môi trường),
 * tạo ra JwtParser để Gateway tự verify/parse token mà không cần gọi
 * sang auth-service.
 *
 * Lý do dùng HMAC symmetric key thay vì OAuth2 Resource Server:
 *   - auth-service ký token bằng HMAC-SHA256 (symmetric)
 *   - OAuth2 Resource Server chuẩn dùng asymmetric (RSA/EC) hoặc JWK URI
 *   - Để giữ đồng bộ cùng thư viện jjwt, ta tự xây JwtParser reactive
 */
@Configuration
public class JwtGatewayConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecretKey gatewaySecretKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    public JwtParser gatewayJwtParser(SecretKey gatewaySecretKey) {
        return Jwts.parser()
                .verifyWith(gatewaySecretKey)
                .build();
    }
}
