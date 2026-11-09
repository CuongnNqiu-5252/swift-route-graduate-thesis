package com.swiftroute.notificationservice;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NotificationAuthorization {
    private final RestClient auth;

    @Autowired
    public NotificationAuthorization(@Value("${auth-service.url:http://localhost:8081}") String url) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        auth = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    NotificationAuthorization(RestClient auth) {
        this.auth = auth;
    }

    public void requireOperator(String authorization, UUID tenantId) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            var response = auth.get().uri("/api/auth/context?tenantId={tenant}", tenantId)
                    .header("Authorization", authorization).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null || !(response.get("data") instanceof Map<?, ?> context)) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            }
            boolean systemAdmin = Set.of("SYSTEM_ADMIN", "ADMIN").contains(String.valueOf(context.get("system_role")));
            boolean operator = Set.of("OWNER", "ADMIN", "OPERATOR").contains(String.valueOf(context.get("membership_role")));
            if (!systemAdmin && !operator) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } catch (HttpClientErrorException ex) {
            throw new ResponseStatusException(ex.getStatusCode());
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity service unavailable");
        }
    }

    public UUID requireMemberUserId(String authorization, UUID tenantId, UUID fallbackUserIdHeader) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            var response = auth.get().uri("/api/auth/context?tenantId={tenant}", tenantId)
                    .header("Authorization", authorization).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null || !(response.get("data") instanceof Map<?, ?> context)) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            }
            boolean systemAdmin = Set.of("SYSTEM_ADMIN", "ADMIN").contains(String.valueOf(context.get("system_role")));
            boolean member = Set.of("OWNER", "ADMIN", "OPERATOR", "DRIVER").contains(String.valueOf(context.get("membership_role")));
            if (!systemAdmin && !member) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            Object rawUserId = context.get("user_id");
            if (rawUserId != null && !String.valueOf(rawUserId).isBlank()) {
                return UUID.fromString(String.valueOf(rawUserId));
            }
            if (fallbackUserIdHeader != null) {
                return fallbackUserIdHeader;
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        } catch (HttpClientErrorException ex) {
            throw new ResponseStatusException(ex.getStatusCode());
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity service unavailable");
        }
    }
}
