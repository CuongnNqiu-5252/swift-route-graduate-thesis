package com.swiftroute.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.swiftroute.apigateway.security.JwtClaims;
import com.swiftroute.apigateway.security.JwtVerifier;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtVerifier jwtVerifier;

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        jwtAuthFilter = new JwtAuthFilter(jwtVerifier);
    }

    @Test
    @DisplayName("Password recovery endpoints are public")
    void passwordRecoveryEndpointsArePublic() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        jwtAuthFilter.filter(MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/password/forgot").build()), chain).block();
        jwtAuthFilter.filter(MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/password/reset").build()), chain).block();

        verify(chain, times(2)).filter(any(ServerWebExchange.class));
        verifyNoInteractions(jwtVerifier);
    }

    @Test
    @DisplayName("Non-admin user cannot supply a different X-Tenant-Id (rejected with 403 Forbidden)")
    void nonAdminCannotSpoofDifferentTenant() {
        UUID userTenant = UUID.randomUUID();
        UUID spoofedTenant = UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                .header("X-Tenant-Id", spoofedTenant.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtVerifier.verify("valid.jwt.token"))
                .thenReturn(Mono.just(new JwtClaims("user-123", "USER", userTenant.toString(), "OWNER")));

        jwtAuthFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot spoof a different X-Tenant-Id (rejected with 403 Forbidden)")
    void tenantAdminCannotSpoofDifferentTenant() {
        UUID userTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                .header("X-Tenant-Id", otherTenant.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtVerifier.verify("valid.jwt.token"))
                .thenReturn(Mono.just(new JwtClaims("tenant-admin-1", "USER", userTenant.toString(), "TENANT_ADMIN")));

        jwtAuthFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Non-admin user receives their own authenticated tenant even if header omitted")
    void nonAdminEnforcesTokenTenantContext() {
        UUID userTenant = UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtVerifier.verify("valid.jwt.token"))
                .thenReturn(Mono.just(new JwtClaims("user-123", "USER", userTenant.toString(), "OPERATOR")));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        jwtAuthFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-Tenant-Id")).isEqualTo(userTenant.toString());
        assertThat(forwarded.getFirst("X-System-Role")).isEqualTo("USER");
        assertThat(forwarded.getFirst("X-Tenant-Role")).isEqualTo("OPERATOR");
    }

    @Test
    @DisplayName("SYSTEM_ADMIN can override X-Tenant-Id and forged client identity headers are stripped")
    void systemAdminCanOverrideTenantAndStripsForgedHeaders() {
        UUID homeTenant = UUID.randomUUID();
        UUID targetTenant = UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                .header("X-Tenant-Id", targetTenant.toString())
                .header("X-User-Id", "spoofed-user")
                .header("X-System-Role", "USER")
                .header("X-Tenant-Role", "DRIVER")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtVerifier.verify("valid.jwt.token"))
                .thenReturn(Mono.just(new JwtClaims("sysadmin-1", "SYSTEM_ADMIN", homeTenant.toString(), null)));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        jwtAuthFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-Tenant-Id")).isEqualTo(targetTenant.toString());
        assertThat(forwarded.getFirst("X-User-Id")).isEqualTo("sysadmin-1");
        assertThat(forwarded.getFirst("X-System-Role")).isEqualTo("SYSTEM_ADMIN");
        assertThat(forwarded.getFirst("X-Tenant-Role")).isNull();
    }

    @Test
    @DisplayName("User without tenant supplying malformed X-Tenant-Id is rejected with 400 Bad Request")
    void customerSupplyingMalformedTenantIdIsRejected() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                .header("X-Tenant-Id", "not-a-valid-uuid")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtVerifier.verify("valid.jwt.token"))
                .thenReturn(Mono.just(new JwtClaims("customer-1", "USER", null, null)));

        jwtAuthFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("User without tenant omitting X-Tenant-Id does NOT receive hardcoded phantom tenant ID")
    void customerOmittingTenantDoesNotReceiveHardcodedTenant() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtVerifier.verify("valid.jwt.token"))
                .thenReturn(Mono.just(new JwtClaims("customer-1", "USER", null, null)));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        jwtAuthFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-Tenant-Id")).isNull();
        assertThat(forwarded.getFirst("X-System-Role")).isEqualTo("USER");
    }
}
