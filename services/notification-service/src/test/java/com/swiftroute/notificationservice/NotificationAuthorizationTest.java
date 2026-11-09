package com.swiftroute.notificationservice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class NotificationAuthorizationTest {
    private final UUID tenantId = UUID.randomUUID();
    private MockRestServiceServer server;
    private NotificationAuthorization authorization;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://identity.test");
        server = MockRestServiceServer.bindTo(builder).build();
        authorization = new NotificationAuthorization(builder.build());
    }

    @Test
    void operatorMembershipIsVerifiedForRequestedTenantWithBearerToken() {
        server.expect(requestTo("http://identity.test/api/auth/context?tenantId=" + tenantId))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"data\":{\"system_role\":\"USER\",\"membership_role\":\"OPERATOR\"}}", MediaType.APPLICATION_JSON));
        authorization.requireOperator("Bearer token", tenantId);
        server.verify();
    }

    @Test
    void driverCannotReadOperatorNotifications() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"data\":{\"system_role\":\"DRIVER\",\"membership_role\":\"DRIVER\"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> authorization.requireOperator("Bearer token", tenantId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void membershipInAnotherTenantDoesNotAuthorizeRequestedTenant() {
        server.expect(requestTo("http://identity.test/api/auth/context?tenantId=" + tenantId))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> authorization.requireOperator("Bearer token", tenantId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void missingBearerIsRejectedBeforeCallingIdentityService() {
        assertThatThrownBy(() -> authorization.requireOperator(null, tenantId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        server.verify();
    }
}
