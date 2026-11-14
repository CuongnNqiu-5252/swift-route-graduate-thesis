package com.swiftroute.orderservice.security;

import java.time.Duration;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import com.swiftroute.orderservice.exception.*;

import io.jsonwebtoken.Claims;

@Component
public class TenantIdentityClient {
    private final RestClient client;
    private final String key;
    private final JwtTokenValidator jwtTokenValidator;

    public TenantIdentityClient(@Value("${auth-service.url:http://localhost:8081}") String url,
            @Value("${tenant.internal-key:}") String key,
            JwtTokenValidator jwtTokenValidator) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.key = key;
        this.jwtTokenValidator = jwtTokenValidator;
    }

    /**
     * Stateless JWT context extraction (aligned with driver-service and notification-service).
     * Verifies the Bearer token signature locally without calling auth-service.
     */
    public Map<String, Object> statelessContext(String authorization, UUID expectedTenantId) {
        Claims claims = jwtTokenValidator.parseAndValidate(authorization);
        if (claims == null || claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String systemRole = claims.get("system_role", String.class);
        String tokenTenantId = claims.get("tenant_id", String.class);
        String tenantRole = claims.get("tenant_role", String.class);
        boolean isSystemAdmin = "SYSTEM_ADMIN".equalsIgnoreCase(systemRole);

        if (expectedTenantId != null && !isSystemAdmin
                && (tokenTenantId == null || tokenTenantId.isBlank()
                        || !expectedTenantId.toString().equals(tokenTenantId))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("user_id", claims.getSubject());
        if (systemRole != null) {
            result.put("system_role", systemRole);
        }
        if (tokenTenantId != null) {
            result.put("tenant_id", tokenTenantId);
        }
        if (tenantRole != null) {
            result.put("membership_role", tenantRole);
        }
        return result;
    }

    /**
     * Live identity lookup against auth-service for one-time tenant registration
     * where fresh email_verified and email fields are required from auth_db.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> context(String authorization, UUID tenantId) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        try {
            Map<String, Object> body = client.get()
                .uri("/api/auth/context" + (tenantId == null ? "" : "?tenantId=" + tenantId))
                .header("Authorization", authorization)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (body == null || !(body.get("data") instanceof Map)) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }

            return (Map<String, Object>) body.get("data");
        } catch (HttpClientErrorException ex) {
            throw new AppException(ex.getStatusCode().value() == 401 ? ErrorCode.UNAUTHENTICATED : ErrorCode.ACCESS_DENIED);
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    public void provision(UUID tenantId, UUID ownerId) {
        if (key.isBlank()) throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        try {
            client.post().uri("/internal/tenant-memberships/provision").header("X-Service-Key", key)
                .body(Map.of("tenant_id", tenantId, "owner_user_id", ownerId)).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    public static boolean systemAdmin(Map<String, Object> context) {
        return "SYSTEM_ADMIN".equalsIgnoreCase(String.valueOf(context.get("system_role")));
    }

    public void requireOwner(Map<String, Object> context) {
        if (!"OWNER".equals(context.get("membership_role"))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
