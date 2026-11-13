package com.swiftroute.driverservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;

class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "swiftroute-test-jwt-secret-key-at-least-32-bytes";
    private static final String INTERNAL_KEY = "test-internal-service-key";

    private SecretKey signingKey;
    private JwtAuthenticationFilter filter;
    private UUID userId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        filter = new JwtAuthenticationFilter(
                JwtAuthenticationFilter.buildJwtParser(TEST_SECRET),
                INTERNAL_KEY);
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String signToken(String systemRole, String tokenTenantId, String tenantRole) {
        Instant now = Instant.now();
        var jwt = Jwts.builder()
                .subject(userId.toString())
                .claim("system_role", systemRole)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)));
        if (tokenTenantId != null) {
            jwt.claim("tenant_id", tokenTenantId);
        }
        if (tenantRole != null) {
            jwt.claim("tenant_role", tenantRole);
        }
        return "Bearer " + jwt.signWith(signingKey).compact();
    }

    @Test
    void tenantScopedOperatorJwtAuthenticatesWithUnifiedOperatorRoleWithoutHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/drivers");
        request.addHeader("Authorization", signToken("USER", tenantId.toString(), "OPERATOR"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_OPERATOR");
        assertThat(request.getAttribute("authorizedUserId")).isEqualTo(userId);
        assertThat(request.getAttribute("authorizedTenantId")).isEqualTo(tenantId);
        assertThat(request.getAttribute("systemAdmin")).isEqualTo(false);
    }

    @Test
    void tenantScopedTenantAdminJwtAuthenticatesWithTenantAdminRoleWithoutTenantHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/earnings/policy");
        request.addHeader("Authorization", signToken("USER", tenantId.toString(), "TENANT_ADMIN"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_TENANT_ADMIN");
        assertThat(request.getAttribute("authorizedTenantId")).isEqualTo(tenantId);
        assertThat(request.getAttribute("authorizedUserId")).isEqualTo(userId);
    }

    @Test
    void systemAdminJwtAuthenticatesAcrossRequestedTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/drivers");
        request.addHeader("Authorization", signToken("SYSTEM_ADMIN", null, null));
        request.addHeader("X-Tenant-Id", tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SYSTEM_ADMIN");
        assertThat(request.getAttribute("systemAdmin")).isEqualTo(true);
        assertThat(request.getAttribute("authorizedTenantId")).isEqualTo(tenantId);
    }

    @Test
    void viewerJwtAllowedOnGetAndBlockedOnMutations() throws Exception {
        String viewerBearer = signToken("USER", tenantId.toString(), "VIEWER");

        MockHttpServletRequest getReq = new MockHttpServletRequest("GET", "/api/drivers");
        getReq.addHeader("Authorization", viewerBearer);
        MockHttpServletResponse getRes = new MockHttpServletResponse();
        FilterChain getChain = mock(FilterChain.class);

        filter.doFilter(getReq, getRes, getChain);
        verify(getChain).doFilter(getReq, getRes);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_VIEWER");

        SecurityContextHolder.clearContext();

        MockHttpServletRequest postReq = new MockHttpServletRequest("POST", "/api/drivers");
        postReq.addHeader("Authorization", viewerBearer);
        MockHttpServletResponse postRes = new MockHttpServletResponse();
        FilterChain postChain = mock(FilterChain.class);

        filter.doFilter(postReq, postRes, postChain);
        assertThat(postRes.getStatus()).isEqualTo(403);
        verifyNoInteractions(postChain);
    }

    @Test
    void unscopedUserJwtIsRejectedWithForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/drivers");
        request.addHeader("Authorization", signToken("USER", null, null));
        request.addHeader("X-Tenant-Id", tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void crossTenantHeaderSpoofIsRejectedWithForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/drivers");
        request.addHeader("Authorization", signToken("USER", UUID.randomUUID().toString(), "OWNER"));
        request.addHeader("X-Tenant-Id", tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void spoofedIdentityAndRoleHeadersAreIgnoredAndJwtClaimsAuthoritative() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/drivers/me");
        request.addHeader("Authorization", signToken("USER", tenantId.toString(), "DRIVER"));
        request.addHeader("X-System-Role", "SYSTEM_ADMIN");
        request.addHeader("X-Tenant-Role", "OWNER");
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_DRIVER");
        assertThat(request.getAttribute("authorizedUserId")).isEqualTo(userId);
        assertThat(request.getAttribute("systemAdmin")).isEqualTo(false);
        assertThat(request.getAttribute("effectiveRole")).isEqualTo("DRIVER");
    }

    @Test
    void invalidJwtSignatureIsRejectedWithUnauthorized() throws Exception {
        SecretKey wrongKey = Keys.hmacShaKeyFor("wrong-signing-key-must-be-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        String forged = "Bearer " + Jwts.builder()
                .subject(userId.toString())
                .claim("system_role", "SYSTEM_ADMIN")
                .signWith(wrongKey)
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/drivers/me");
        request.addHeader("Authorization", forged);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void internalServiceKeyBypassesJwtForAllowedGetEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/drivers");
        request.addHeader("X-Service-Key", INTERNAL_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}

