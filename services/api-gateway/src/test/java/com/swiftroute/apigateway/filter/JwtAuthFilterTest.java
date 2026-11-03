package com.swiftroute.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
                .thenReturn(Mono.just(new JwtClaims("user-123", "OWNER", userTenant.toString())));

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
                .thenReturn(Mono.just(new JwtClaims("user-123", "STAFF", userTenant.toString())));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        jwtAuthFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-Tenant-Id"))
                .isEqualTo(userTenant.toString());
    }

    @Test
    @DisplayName("Customer supplying malformed X-Tenant-Id is rejected with 400 Bad Request")
    void customerSupplyingMalformedTenantIdIsRejected() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                .header("X-Tenant-Id", "not-a-valid-uuid")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtVerifier.verify("valid.jwt.token"))
                .thenReturn(Mono.just(new JwtClaims("customer-1", "CUSTOMER", null)));

        jwtAuthFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Customer omitting X-Tenant-Id does NOT receive hardcoded phantom tenant ID")
    void customerOmittingTenantDoesNotReceiveHardcodedTenant() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtVerifier.verify("valid.jwt.token"))
                .thenReturn(Mono.just(new JwtClaims("customer-1", "CUSTOMER", null)));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        jwtAuthFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-Tenant-Id"))
                .isNull();
    }
}
