package com.swiftroute.orderservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class TenantIdentityClientTest {

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    private TenantIdentityClient tenantIdentityClient;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantIdentityClient = new TenantIdentityClient("http://localhost:8081", "test-key", jwtTokenValidator);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("statelessContext throws ACCESS_DENIED when expectedTenantId is not null and token has no tenant claim")
    void statelessContextThrowsAccessDeniedWhenExpectedTenantNotNullAndTokenTenantNull() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(null);
        when(claims.get("tenant_role", String.class)).thenReturn(null);
        when(jwtTokenValidator.parseAndValidate("Bearer token.without.tenant")).thenReturn(claims);

        assertThatThrownBy(() -> tenantIdentityClient.statelessContext("Bearer token.without.tenant", tenantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("statelessContext throws ACCESS_DENIED when expectedTenantId is not null and token tenant is blank")
    void statelessContextThrowsAccessDeniedWhenExpectedTenantNotNullAndTokenTenantBlank() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn("   ");
        when(claims.get("tenant_role", String.class)).thenReturn(null);
        when(jwtTokenValidator.parseAndValidate("Bearer token.blank.tenant")).thenReturn(claims);

        assertThatThrownBy(() -> tenantIdentityClient.statelessContext("Bearer token.blank.tenant", tenantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("statelessContext throws ACCESS_DENIED when token tenant mismatches expectedTenantId")
    void statelessContextThrowsAccessDeniedWhenTokenTenantMismatchesExpectedTenant() {
        UUID otherTenantId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(otherTenantId.toString());
        when(claims.get("tenant_role", String.class)).thenReturn("OPERATOR");
        when(jwtTokenValidator.parseAndValidate("Bearer token.other.tenant")).thenReturn(claims);

        assertThatThrownBy(() -> tenantIdentityClient.statelessContext("Bearer token.other.tenant", tenantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("statelessContext succeeds when token tenant matches expectedTenantId")
    void statelessContextSucceedsWhenTokenTenantMatchesExpectedTenant() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId.toString());
        when(claims.get("tenant_role", String.class)).thenReturn("OWNER");
        when(jwtTokenValidator.parseAndValidate("Bearer valid.token")).thenReturn(claims);

        Map<String, Object> context = tenantIdentityClient.statelessContext("Bearer valid.token", tenantId);

        assertThat(context).isNotNull();
        assertThat(context.get("user_id")).isEqualTo(userId.toString());
        assertThat(context.get("tenant_id")).isEqualTo(tenantId.toString());
        assertThat(context.get("membership_role")).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("statelessContext allows SYSTEM_ADMIN even without matching tenant claim")
    void statelessContextAllowsSystemAdminWithoutMatchingTenantClaim() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("SYSTEM_ADMIN");
        when(claims.get("tenant_id", String.class)).thenReturn(null);
        when(claims.get("tenant_role", String.class)).thenReturn(null);
        when(jwtTokenValidator.parseAndValidate("Bearer admin.token")).thenReturn(claims);

        Map<String, Object> context = tenantIdentityClient.statelessContext("Bearer admin.token", tenantId);

        assertThat(context).isNotNull();
        assertThat(context.get("user_id")).isEqualTo(userId.toString());
        assertThat(context.get("system_role")).isEqualTo("SYSTEM_ADMIN");
        assertThat(TenantIdentityClient.systemAdmin(context)).isTrue();
    }

    @Test
    @DisplayName("statelessContext allows unscoped call when expectedTenantId is null")
    void statelessContextAllowsUnscopedQueryWhenExpectedTenantNull() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(null);
        when(claims.get("tenant_role", String.class)).thenReturn(null);
        when(jwtTokenValidator.parseAndValidate("Bearer unscoped.token")).thenReturn(claims);

        Map<String, Object> context = tenantIdentityClient.statelessContext("Bearer unscoped.token", null);

        assertThat(context).isNotNull();
        assertThat(context.get("user_id")).isEqualTo(userId.toString());
        assertThat(context.get("system_role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("statelessContext throws UNAUTHENTICATED on missing or invalid token")
    void statelessContextThrowsUnauthenticatedOnInvalidToken() {
        when(jwtTokenValidator.parseAndValidate("Bearer invalid.token")).thenReturn(null);

        assertThatThrownBy(() -> tenantIdentityClient.statelessContext("Bearer invalid.token", tenantId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
}
