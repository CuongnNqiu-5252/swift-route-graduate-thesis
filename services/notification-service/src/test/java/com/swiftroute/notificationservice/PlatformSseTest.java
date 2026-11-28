package com.swiftroute.notificationservice;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformSseTest {
    private static final String SECRET = "platform-sse-test-key-at-least-thirty-two-characters";
    private final UUID tenant = UUID.randomUUID();
    private String token(boolean admin) {
        return "Bearer " + Jwts.builder().subject(UUID.randomUUID().toString())
                .claim("system_role", admin ? "SYSTEM_ADMIN" : "USER")
                .claim("tenant_id", tenant.toString()).claim("tenant_role", "OWNER")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test
    void existingSseTransportKeepsTenantAndAdminEventsIsolatedAndSharesHeartbeats() throws Exception {
        var sse = new NotificationSseService();
        var mvc = MockMvcBuilders.standaloneSetup(new DispatchAlertController(new NotificationAuthorization(SECRET), sse, mock(JdbcTemplate.class))).build();
        var admin = mvc.perform(get("/api/notifications/platform/stream").header("Authorization", token(true)))
                .andExpect(request().asyncStarted()).andReturn();
        var operator = mvc.perform(get("/api/notifications/stream").header("Authorization", token(false)))
                .andExpect(request().asyncStarted()).andReturn();
        try {
            sse.broadcastPlatform("platform-audit", Map.of("audit_id", "admin-only"));
            sse.broadcast(tenant, "dispatch-alert", Map.of("message", "tenant-only"));
            sse.sendHeartbeat();
            assertThat(admin.getResponse().getContentAsString()).contains("event:connect", "platform-audit", "admin-only", "event:ping").doesNotContain("tenant-only");
            assertThat(operator.getResponse().getContentAsString()).contains("dispatch-alert", "tenant-only", "event:ping").doesNotContain("admin-only", "platform-audit");
        } finally {
            admin.getRequest().getAsyncContext().complete();
            operator.getRequest().getAsyncContext().complete();
        }
    }

    @Test
    void tenantOwnerIsRejectedAndAdminTimeoutIsBoundedByToken() throws Exception {
        var sse = new NotificationSseService();
        var mvc = MockMvcBuilders.standaloneSetup(new DispatchAlertController(new NotificationAuthorization(SECRET), sse, mock(JdbcTemplate.class))).build();
        mvc.perform(get("/api/notifications/platform/stream").header("Authorization", token(false))).andExpect(status().isForbidden());
        assertThat(sse.subscribePlatform(12345).getTimeout()).isEqualTo(12345);
    }
}
