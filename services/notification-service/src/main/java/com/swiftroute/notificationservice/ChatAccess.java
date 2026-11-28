package com.swiftroute.notificationservice;

import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;

@Component
class ChatAccess {
    record Account(UUID tenant, UUID user, Instant expiresAt) {}
    record Member(UUID id, String name, String role) {}

    private static final Set<String> OPERATORS = Set.of("OWNER", "TENANT_ADMIN", "OPERATOR");
    private final JwtParser jwt;
    private final RestClient auth;

    ChatAccess(@Value("${jwt.secret:your_super_secret_key_change_in_production}") String secret,
            @Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authUrl,
            @Value("${TENANT_INTERNAL_KEY:}") String serviceKey) {
        jwt = NotificationAuthorization.buildJwtParser(secret);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        auth = RestClient.builder().baseUrl(authUrl).defaultHeader("X-Service-Key", serviceKey)
                .requestFactory(factory).build();
    }

    Account account(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            var claims = jwt.parseSignedClaims(authorization.substring(7)).getPayload();
            if (claims.get("tenant_id", String.class) == null || claims.getExpiration() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            return new Account(UUID.fromString(claims.get("tenant_id", String.class)),
                    UUID.fromString(claims.getSubject()), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    List<Member> contacts(Account account) {
        if (!account.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Member[] members;
        try {
            members = auth.get().uri("/internal/tenant-memberships/{tenant}/chat-members", account.tenant())
                    .retrieve().body(Member[].class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không thể kiểm tra thành viên");
        }
        var active = members == null ? List.<Member>of() : Arrays.asList(members);
        var role = active.stream().filter(member -> account.user().equals(member.id())).map(Member::role)
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if ("DRIVER".equals(role)) {
            return active.stream().filter(member -> OPERATORS.contains(member.role())).toList();
        }
        if (!OPERATORS.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return active.stream().filter(member -> "DRIVER".equals(member.role())).toList();
    }

    void requirePeer(Account account, UUID peer) {
        if (peer == null || contacts(account).stream().noneMatch(member -> peer.equals(member.id()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
