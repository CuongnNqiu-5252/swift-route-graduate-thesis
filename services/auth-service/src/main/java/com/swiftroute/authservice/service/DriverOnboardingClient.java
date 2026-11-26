package com.swiftroute.authservice.service;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DriverOnboardingClient {
    private final RestClient client;
    private final String key;
    public DriverOnboardingClient(@Value("${driver-service.url:http://driver-service:8083}") String url,
            @Value("${tenant.internal-key:}") String key) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.key = key;
    }
    public void validate(UUID tenantId, UUID driverId) {
        if (key.isBlank()) throw new IllegalStateException("Driver onboarding credential is not configured");
        client.get().uri("/internal/fleet/" + tenantId + "/profiles/" + driverId)
                .header("X-Service-Key", key).retrieve().toBodilessEntity();
    }
    public void link(UUID tenantId, UUID driverId, UUID userId) {
        if (key.isBlank()) throw new IllegalStateException("Driver onboarding credential is not configured");
        client.post().uri("/internal/fleet/" + tenantId + "/profiles/" + driverId + "/link")
                .header("X-Service-Key", key).body(Map.of("user_id", userId)).retrieve().toBodilessEntity();
    }
    public void provision(UUID tenantId, UUID userId, String fullName, String phone) {
        if (key.isBlank()) throw new IllegalStateException("Driver onboarding credential is not configured");
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("user_id", userId);
        body.put("full_name", fullName);
        body.put("phone", phone);
        client.post().uri("/internal/fleet/" + tenantId + "/profiles/provision")
                .header("X-Service-Key", key).body(body).retrieve().toBodilessEntity();
    }

    public record ActivateDevicePayload(
            UUID userId,
            String installationId,
            UUID sessionId,
            String deviceOs,
            String osVersion,
            String appVersion,
            String deviceModel) {
    }

    @SuppressWarnings("unchecked")
    public UUID activateDevice(UUID tenantId, UUID driverId, ActivateDevicePayload payload) {
        if (key.isBlank()) throw new IllegalStateException("Driver onboarding credential is not configured");
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("user_id", payload.userId());
        body.put("installation_id", payload.installationId());
        body.put("session_id", payload.sessionId());
        body.put("device_os", payload.deviceOs());
        body.put("os_version", payload.osVersion());
        body.put("app_version", payload.appVersion());
        body.put("device_model", payload.deviceModel());
        Map<String, Object> response = client.post()
                .uri("/internal/fleet/" + tenantId + "/drivers/" + driverId + "/activate-device")
                .header("X-Service-Key", key)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response != null && response.get("revoked_session_id") != null) {
            String raw = response.get("revoked_session_id").toString();
            if (!raw.isBlank()) {
                return UUID.fromString(raw);
            }
        }
        return null;
    }
}
