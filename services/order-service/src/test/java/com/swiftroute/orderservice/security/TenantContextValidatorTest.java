package com.swiftroute.orderservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.TenantRepository;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class TenantContextValidatorTest {

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private TenantRepository tenantRepository;

    private TenantContextValidator tenantContextValidator;

    private UUID tenantId;
    private Tenant activeTenant;

    @BeforeEach
    void setUp() {
        tenantContextValidator = new TenantContextValidator(jwtTokenValidator, tenantRepository);
        tenantId = UUID.randomUUID();
        activeTenant = Tenant.builder()
                .id(tenantId)
                .businessName("Công ty TNHH Giao Vận Cần Thơ")
                .status(TenantStatus.APPROVED)
                .build();
    }

    @Test
    @DisplayName("Should successfully validate tenant access when JWT tenant matches header")
    void validateTenantAccessWithValidJwtAndMatchingTenantSuccess() {
        String authHeader = "Bearer valid.token.here";
        Claims claims = mock(Claims.class);
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId.toString());
        when(claims.get("_ROLE", String.class)).thenReturn("STAFF");
        when(jwtTokenValidator.parseAndValidate(authHeader)).thenReturn(claims);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant));

        Tenant result = tenantContextValidator.validateTenantAccess(tenantId.toString(), authHeader);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(tenantId);
        assertThat(result.getStatus()).isEqualTo(TenantStatus.APPROVED);
    }

    @Test
    @DisplayName("Should reject with ACCESS_DENIED when non-admin attempts cross-tenant access")
    void validateTenantAccessNonAdminCrossTenantThrowsAccessDenied() {
        String authHeader = "Bearer valid.token.here";
        UUID anotherTenantId = UUID.randomUUID();

        Claims claims = mock(Claims.class);
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId.toString());
        when(claims.get("_ROLE", String.class)).thenReturn("STAFF");
        when(jwtTokenValidator.parseAndValidate(authHeader)).thenReturn(claims);

        // User belongs to tenantId, but tries to access anotherTenantId in header
        assertThatThrownBy(() -> tenantContextValidator.validateTenantAccess(anotherTenantId.toString(), authHeader))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Should reject with ACCESS_DENIED when non-admin user has no tenant claim")
    void validateTenantAccessNonAdminWithoutTenantThrowsAccessDenied() {
        String authHeader = "Bearer valid.token.here";

        Claims claims = mock(Claims.class);
        when(claims.get("tenant_id", String.class)).thenReturn(null);
        when(claims.get("_ROLE", String.class)).thenReturn("CUSTOMER");
        when(claims.getSubject()).thenReturn("customer-uuid");
        when(jwtTokenValidator.parseAndValidate(authHeader)).thenReturn(claims);

        assertThatThrownBy(() -> tenantContextValidator.validateTenantAccess(tenantId.toString(), authHeader))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Should allow ADMIN user to specify target tenant in header")
    void validateTenantAccessAdminWithTargetTenantSuccess() {
        String authHeader = "Bearer admin.token.here";

        Claims claims = mock(Claims.class);
        when(claims.get("tenant_id", String.class)).thenReturn(null);
        when(claims.get("_ROLE", String.class)).thenReturn("ADMIN");
        when(jwtTokenValidator.parseAndValidate(authHeader)).thenReturn(claims);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant));

        Tenant result = tenantContextValidator.validateTenantAccess(tenantId.toString(), authHeader);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Should reject with UNAUTHENTICATED when JWT is invalid")
    void validateTenantAccessInvalidJwtThrowsUnauthenticated() {
        String authHeader = "Bearer invalid.token";
        when(jwtTokenValidator.parseAndValidate(authHeader)).thenReturn(null);

        assertThatThrownBy(() -> tenantContextValidator.validateTenantAccess(tenantId.toString(), authHeader))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("Should reject with TENANT_SUSPENDED when target tenant is suspended in database")
    void validateTenantAccessSuspendedTenantThrowsException() {
        Tenant suspendedTenant = Tenant.builder()
                .id(tenantId)
                .status(TenantStatus.SUSPENDED)
                .build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(suspendedTenant));

        assertThatThrownBy(() -> tenantContextValidator.validateTenantAccess(tenantId.toString(), null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TENANT_SUSPENDED);
    }

    @Test
    @DisplayName("Should reject with TENANT_NOT_FOUND when tenant does not exist in database")
    void validateTenantAccessNotFoundTenantThrowsException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantContextValidator.validateTenantAccess(tenantId.toString(), null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TENANT_NOT_FOUND);
    }
}

