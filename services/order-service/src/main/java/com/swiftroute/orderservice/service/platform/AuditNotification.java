package com.swiftroute.orderservice.service.platform;

import java.util.UUID;
import com.swiftroute.orderservice.entity.AuditLog;
import jakarta.persistence.PostPersist;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reuses the existing transactional outbox and its RabbitMQ publisher. */
@Component
@RequiredArgsConstructor
public class AuditNotification {
    private final JdbcTemplate jdbc;

    @PostPersist
    public void saved(AuditLog record) {
        enqueue(jdbc, record.getId());
    }

    public static void enqueue(JdbcTemplate jdbc, UUID id) {
        // JDBC is intentional inside @PostPersist: do not mutate Hibernate's
        // persistence queue by saving another JPA entity during its flush.
        jdbc.update("""
                INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,event_type,payload,created_at,retry_count)
                VALUES (?, 'platform_audit', ?, 'platform.audit-recorded.v1',
                        jsonb_build_object('event_type','platform.audit-recorded.v1','audit_id',?::text),
                        now() AT TIME ZONE 'UTC', 0)
                ON CONFLICT (event_id) DO NOTHING
                """, id, id, id.toString());
    }
}
