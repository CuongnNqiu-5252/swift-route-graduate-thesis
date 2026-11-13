package com.swiftroute.orderservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class TenantRequestInterceptorTest {

    @Mock
    private JwtTokenValidator tokens;

    private TenantRequestInterceptor interceptor;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        interceptor = new TenantRequestInterceptor(tokens);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Stateless JWT: resolves tenant and user from JWT even without X-Tenant-Id or X-User-Id headers")
    void statelessJwtAllowsScopedOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Authorization", "Bearer valid.token");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId.toString());
        when(claims.get("tenant_role", String.class)).thenReturn("OPERATOR");
        when(tokens.parseAndValidate("Bearer valid.token")).thenReturn(claims);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("authorizedUserId")).isEqualTo(userId);
        assertThat(request.getAttribute("authorizedTenantId")).isEqualTo(tenantId);
        assertThat(request.getAttribute("effectiveRole")).isEqualTo("OPERATOR");
    }

    @Test
    @DisplayName("Stateless JWT: ignores spoofed X-User-Id, X-System-Role, and X-Tenant-Role headers")
    void statelessJwtIgnoresSpoofedIdentityHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer valid.token");
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-System-Role", "SYSTEM_ADMIN");
        request.addHeader("X-Tenant-Role", "OWNER");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId.toString());
        when(claims.get("tenant_role", String.class)).thenReturn("DRIVER");
        when(tokens.parseAndValidate("Bearer valid.token")).thenReturn(claims);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("authorizedUserId")).isEqualTo(userId);
        assertThat(request.getAttribute("authorizedTenantId")).isEqualTo(tenantId);
        assertThat(request.getAttribute("effectiveRole")).isEqualTo("DRIVER");
    }

    @Test
    @DisplayName("Stateless JWT: rejects cross-tenant X-Tenant-Id override by non-admin")
    void crossTenantHeaderOverrideIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer valid.token");
        request.addHeader("X-Tenant-Id", UUID.randomUUID().toString());

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId.toString());
        when(claims.get("tenant_role", String.class)).thenReturn("OPERATOR");
        when(tokens.parseAndValidate("Bearer valid.token")).thenReturn(claims);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Stateless JWT: should block VIEWER role from mutating state via POST")
    void statelessJwtBlocksViewerOnPost() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Authorization", "Bearer valid.token");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId.toString());
        when(claims.get("tenant_role", String.class)).thenReturn("VIEWER");
        when(tokens.parseAndValidate("Bearer valid.token")).thenReturn(claims);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Stateless JWT: rejects unscoped USER token when tenant_id claim is missing")
    void unscopedUserJwtIsRejectedWithoutTenantClaim() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer valid.token");
        request.addHeader("X-Tenant-Id", tenantId.toString());

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(null);
        when(claims.get("tenant_role", String.class)).thenReturn(null);
        when(tokens.parseAndValidate("Bearer valid.token")).thenReturn(claims);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Stateless JWT: allows SYSTEM_ADMIN across any target tenant header or unscoped platform reads")
    void systemAdminAllowsCrossTenantAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer admin.token");
        request.addHeader("X-Tenant-Id", tenantId.toString());

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("SYSTEM_ADMIN");
        when(claims.get("tenant_id", String.class)).thenReturn(null);
        when(claims.get("tenant_role", String.class)).thenReturn(null);
        when(tokens.parseAndValidate("Bearer admin.token")).thenReturn(claims);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("authorizedUserId")).isEqualTo(userId);
        assertThat(request.getAttribute("authorizedTenantId")).isEqualTo(tenantId);
        assertThat(request.getAttribute("effectiveRole")).isEqualTo("SYSTEM_ADMIN");

        MockHttpServletRequest unscopedReq = new MockHttpServletRequest("GET", "/api/orders");
        unscopedReq.addHeader("Authorization", "Bearer admin.token");
        boolean allowedUnscoped = interceptor.preHandle(unscopedReq, new MockHttpServletResponse(), new Object());
        assertThat(allowedUnscoped).isTrue();
        assertThat(unscopedReq.getAttribute("authorizedTenantId")).isNull();
        assertThat(unscopedReq.getAttribute("effectiveRole")).isEqualTo("SYSTEM_ADMIN");
    }
}
