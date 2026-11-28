package com.swiftroute.orderservice.service.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.entity.AuditLog;
import com.swiftroute.orderservice.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PlatformReadServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformReadService reads = new PlatformReadService(jdbc, new ObjectMapper());

    @Test
    void exposesOnlyWhitelistedScalarsAndRedactsCredentialPatterns() {
        var safe = reads.safeDetails("""
                {"password":"secret", "snapshot":{"private":"data"}, "reason":"token=abc Bearer xyz.123", "revision":4,"is_active":true}
                """);
        assertThat(safe).containsKeys("reason", "revision", "is_active").doesNotContainKeys("password", "snapshot");
        assertThat(safe.get("reason").toString()).contains("[REDACTED]").doesNotContain("abc", "xyz.123");
        assertThat(reads.safeDetails("not json")).isEmpty();
    }

    @Test
    void validatesFilterAndCursorPairBeforeQuerying() {
        assertThatThrownBy(() -> new PlatformReadService.Filter("", "UNKNOWN", "", 7, null, null)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> new PlatformReadService.Filter("", "", "", 10000, null, null)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> new PlatformReadService.Filter("", "", "", 7, Instant.now(), null)).isInstanceOf(AppException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void categoryAndSeverityMatchExistingProducerNames() {
        assertThat(PlatformReadService.category("TENANT_DELIVERY_ZONE", "DELIVERY_ZONE_UPDATED")).isEqualTo("CONFIGURATION");
        assertThat(PlatformReadService.category("TENANT_VERIFICATION", "TENANT_SUSPENDED")).isEqualTo("ACCESS");
        assertThat(PlatformReadService.severity("PROVISIONING_FAILED")).isEqualTo("ERROR");
        assertThat(PlatformReadService.severity("VERIFICATION_REJECTED")).isEqualTo("WARNING");
    }

    @Test
    void historyUsesBoundParametersAndDeterministicKeysetPagination() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        String search = "' OR true --";
        reads.history(new PlatformReadService.Filter(search, "ACCESS", "WARNING", 7, Instant.now(), UUID.randomUUID()), 50);
        verify(jdbc).query(argThat(sql -> sql.contains("(a.created_at,a.audit_id)<(?,?)")
                        && sql.contains("ORDER BY a.created_at DESC,a.audit_id DESC") && !sql.contains(search)),
                any(RowMapper.class), any(Object[].class));
    }

    @Test
    void persistenceCallbackReusesOutboxWithOnlyTheAuditId() {
        UUID id = UUID.randomUUID();
        new AuditNotification(jdbc).saved(AuditLog.builder().id(id).build());
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO outbox_events") && sql.contains("platform.audit-recorded.v1")
                && sql.contains("ON CONFLICT (event_id) DO NOTHING")), eq(id), eq(id), eq(id.toString()));
    }
}
