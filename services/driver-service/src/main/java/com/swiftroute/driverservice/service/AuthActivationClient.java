package com.swiftroute.driverservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Internal HTTP client used by driver-service to coordinate activation key lifecycle
 * and session revocation with auth-service.
 */
@Component
public class AuthActivationClient {

    private final RestClient restClient;
    private final String internalKey;

    public AuthActivationClient(
            @Value("${auth-service.url:${AUTH_SERVICE_URL:http://auth-service:8081}}") String authServiceUrl,
            @Value("${tenant.internal-key:}") String internalKey) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder().baseUrl(authServiceUrl).requestFactory(factory).build();
        this.internalKey = internalKey == null ? "" : internalKey;
    }

    public record IssuedKeyPayload(
            UUID driver_id,
            UUID user_id,
            String activation_key,
            String formatted_activation_key,
            Instant expires_at,
            boolean email_queued) {
    }

    public record KeyStatePayload(
            UUID driver_id,
            String key_state,
            Instant expires_at,
            Instant created_at,
            Instant used_at,
            Instant revoked_at) {
    }

    public record SessionRevokeItem(
            UUID driver_id,
            UUID user_id,
            UUID session_id) {
    }

    public IssuedKeyPayload issueKey(
            UUID tenantId,
            UUID driverId,
            UUID existingUserId,
            String fullName,
            String phone,
            String email,
            UUID createdBy,
            UUID revokeSessionId,
            boolean sendEmail) {
        assertConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("existing_user_id", existingUserId);
        body.put("full_name", fullName);
        body.put("phone", phone);
        body.put("email", email);
        body.put("created_by", createdBy);
        body.put("revoke_session_id", revokeSessionId);
        body.put("send_email", sendEmail);

        return restClient.post()
                .uri("/internal/driver-activations/{tenantId}/drivers/{driverId}/keys", tenantId, driverId)
                .header("X-Service-Key", internalKey)
                .body(body)
                .retrieve()
                .body(IssuedKeyPayload.class);
    }

    public void deactivateSessions(UUID tenantId, List<UUID> driverIds, List<SessionRevokeItem> sessions) {
        assertConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("driver_ids", driverIds != null ? driverIds : List.of());
        body.put("sessions", sessions != null ? sessions : List.of());

        restClient.post()
                .uri("/internal/driver-activations/{tenantId}/deactivate", tenantId)
                .header("X-Service-Key", internalKey)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public Map<UUID, KeyStatePayload> queryKeyStates(UUID tenantId, Collection<UUID> driverIds) {
        if (driverIds == null || driverIds.isEmpty()) {
            return Map.of();
        }
        assertConfigured();
        Map<String, Object> body = Map.of("driver_ids", driverIds);
        Map<UUID, KeyStatePayload> result = restClient.post()
                .uri("/internal/driver-activations/{tenantId}/key-states", tenantId)
                .header("X-Service-Key", internalKey)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<UUID, KeyStatePayload>>() {
                });
        return result != null ? result : Map.of();
    }

    private void assertConfigured() {
        if (internalKey.isBlank()) {
            throw new IllegalStateException("TENANT_INTERNAL_KEY is not configured for auth-service communication");
        }
    }
}
