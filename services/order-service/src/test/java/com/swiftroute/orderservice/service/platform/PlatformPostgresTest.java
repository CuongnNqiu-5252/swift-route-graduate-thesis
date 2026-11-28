package com.swiftroute.orderservice.service.platform;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Opt-in verification of read-only platform queries against the existing schema. */
@EnabledIfEnvironmentVariable(named = "PLATFORM_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
class PlatformPostgresTest {
    private java.sql.Connection connection() throws Exception {
        return DriverManager.getConnection(System.getenv("PLATFORM_TEST_JDBC_URL"),
                System.getenv("PLATFORM_TEST_DB_USER"), System.getenv("PLATFORM_TEST_DB_PASSWORD"));
    }

    @Test
    void existingSchemaSupportsReadOnlyOverviewAndAuditQueries() throws Exception {
        try (var connection = connection()) {
            connection.setReadOnly(true); connection.setAutoCommit(false);
            var reads = new PlatformReadService(new JdbcTemplate(new SingleConnectionDataSource(connection, true)), new ObjectMapper());
            var snapshot = reads.snapshot();
            assertThat(snapshot.overview()).containsKeys("tenant_statuses", "review_priority", "provisioning_attention", "activity_days");
            assertThat((java.util.List<?>) snapshot.overview().get("activity_days")).hasSize(7);
            assertThat(snapshot.audit().entries()).hasSizeLessThanOrEqualTo(100);
            reads.history(new PlatformReadService.Filter("' OR true --", "ACCESS", "WARNING", 7, null, null), 50);
            connection.rollback();
        }
    }

    @Test
    void auditSignalUsesExistingOutboxAndRollsBackWithItsTransaction() throws Exception {
        UUID id = UUID.randomUUID();
        try (var writer = connection(); var observer = connection()) {
            writer.setAutoCommit(false);
            try {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(writer, true));
                AuditNotification.enqueue(jdbc, id);
                AuditNotification.enqueue(jdbc, id); // same audit signal is idempotent
                assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_id=?", Long.class, id)).isEqualTo(1);
                try (var query = observer.prepareStatement("SELECT count(*) FROM outbox_events WHERE event_id=?")) {
                    query.setObject(1, id);
                    try (var result = query.executeQuery()) { result.next(); assertThat(result.getLong(1)).isZero(); }
                    writer.rollback();
                    try (var result = query.executeQuery()) { result.next(); assertThat(result.getLong(1)).isZero(); }
                }
            } finally { writer.rollback(); }
        }
    }

}
