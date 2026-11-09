package com.swiftroute.orderservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.TenantRepository;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class TenantRequestInterceptorTest {

    @Mock
    private JwtTokenValidator tokens;

    @Mock
    private TenantIdentityClient identities;

    @Mock
    private TenantRepository tenants;

    private TenantRequestInterceptor interceptor;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        interceptor = new TenantRequestInterceptor(tokens, identities, tenants);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Fast-path: should validate locally without remote HTTP call when JWT has matching tenant claim")
    void fastPathBypassesRemoteAuthService() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Authorization", "Bearer valid.token");
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-User-Role", "OPERATOR");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("_ROLE", String.class)).thenReturn("OPERATOR");
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId.toString());
        when(claims.get("system_role", String.class)).thenReturn("CUSTOMER");
        when(tokens.parseAndValidate("Bearer valid.token")).thenReturn(claims);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("authorizedTenantId")).isEqualTo(tenantId);
        // Verify remote HTTP context was NEVER called thanks to local fast-path
        verify(identities, never()).context(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Fast-path: should block VIEWER role from mutating state via POST")
    void fastPathBlocksViewerOnPost() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Authorization", "Bearer valid.token");
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-User-Role", "VIEWER");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("_ROLE", String.class)).thenReturn("VIEWER");
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId.toString());
        when(claims.get("system_role", String.class)).thenReturn("CUSTOMER");
        when(tokens.parseAndValidate("Bearer valid.token")).thenReturn(claims);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Fallback: calls identities.context when token does not contain tenant claim")
    void fallbackCallsRemoteServiceWhenNoTenantClaim() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer valid.token");
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-User-Role", "OPERATOR");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("_ROLE", String.class)).thenReturn("OPERATOR");
        when(claims.get("tenant_id", String.class)).thenReturn(null); // No tenant in token
        when(tokens.parseAndValidate("Bearer valid.token")).thenReturn(claims);

        when(identities.context("Bearer valid.token", tenantId)).thenReturn(Map.of(
                "membership_role", "OPERATOR",
                "system_role", "CUSTOMER"
        ));

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("authorizedTenantId")).isEqualTo(tenantId);
        verify(identities).context("Bearer valid.token", tenantId);
    }
}
