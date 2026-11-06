package com.swiftroute.orderservice.security;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import com.swiftroute.orderservice.exception.*;

@Component
public class TenantIdentityClient {
    private final RestClient client;
    private final String key;
    public TenantIdentityClient(@Value("${auth-service.url:http://localhost:8081}") String url,
            @Value("${tenant.internal-key:}") String key) {
        var factory = new JdkClientHttpRequestFactory(); factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build(); this.key=key;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> context(String authorization, UUID tenantId) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new AppException(ErrorCode.UNAUTHENTICATED);
        try {
            Map<String,Object> body=client.get().uri("/api/auth/context"+(tenantId == null ? "" : "?tenantId="+tenantId))
                .header("Authorization",authorization).retrieve().body(new ParameterizedTypeReference<Map<String,Object>>(){});
            if (body == null || !(body.get("data") instanceof Map)) throw new AppException(ErrorCode.ACCESS_DENIED);
            return (Map<String,Object>)body.get("data");
        } catch (HttpClientErrorException ex) { throw new AppException(ex.getStatusCode().value()==401 ? ErrorCode.UNAUTHENTICATED : ErrorCode.ACCESS_DENIED); }
        catch (RestClientException ex) { throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE); }
    }
    public void provision(UUID tenantId, UUID ownerId) {
        if (key.isBlank()) throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        try { client.post().uri("/internal/tenant-memberships/provision").header("X-Service-Key",key)
            .body(Map.of("tenant_id",tenantId,"owner_user_id",ownerId)).retrieve().toBodilessEntity(); }
        catch (RestClientException ex) { throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE); }
    }
    public static boolean systemAdmin(Map<String,Object> context) {
        return Set.of("SYSTEM_ADMIN","ADMIN").contains(String.valueOf(context.get("system_role")));
    }
    public void requireOwner(Map<String,Object> context) {
        if (!"OWNER".equals(context.get("membership_role"))) throw new AppException(ErrorCode.ACCESS_DENIED);
    }
}
