package com.swiftroute.authservice.service;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TenantDetailsClient {
    private final RestClient client;
    private final String key;
    public TenantDetailsClient(@Value("${order-service.url:http://order-service:8082}") String url,
            @Value("${tenant.internal-key:}") String key) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.key = key;
    }
    public String businessName(UUID tenantId) {
        if (key.isBlank()) throw new IllegalStateException("Tenant service credential is not configured");
        var body = client.get().uri("/internal/tenants/" + tenantId).header("X-Service-Key", key)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
        if (body == null || body.get("businessName") == null) throw new IllegalStateException("Business not found");
        return body.get("businessName").toString();
    }
}
