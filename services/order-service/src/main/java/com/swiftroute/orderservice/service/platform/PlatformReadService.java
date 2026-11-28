package com.swiftroute.orderservice.service.platform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformReadService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public record AuditEntry(UUID id, UUID tenantId, String tenantName, String aggregateType,
            UUID aggregateId, String action, UUID actorId, Instant createdAt, String category,
            String severity, Map<String, Object> details) {}
    public record AuditPage(List<AuditEntry> entries, boolean hasMore) {}
    public record Snapshot(Instant asOf, AuditPage audit, Map<String, Object> overview) {}
    public record Filter(String query, String category, String severity, int days, Instant beforeTime, UUID beforeId) {
        public Filter {
            query = query == null ? "" : query.trim();
            category = category == null ? "" : category;
            severity = severity == null ? "" : severity;
            if (query.length() > 120 || !Set.of("", "REVIEW", "ACCESS", "CONFIGURATION", "AUTOMATION", "PROVISIONING", "OTHER").contains(category)
                    || !Set.of("", "INFO", "WARNING", "ERROR").contains(severity)
                    || !Set.of(1, 7, 30, 90).contains(days) || (beforeTime == null) != (beforeId == null)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }

    private static final String COLUMNS = """
            SELECT a.audit_id,a.tenant_id,coalesce(t.business_name,'') AS tenant_name,a.aggregate_type,
                   a.aggregate_id,a.action,a.actor_id,a.created_at,a.details::text AS details
            FROM audit_logs a LEFT JOIN tenants t ON t.tenant_id=a.tenant_id
            """;
    private static final String CATEGORY = """
            CASE WHEN a.action IN ('TENANT_SUSPENDED','TENANT_REACTIVATED') THEN 'ACCESS'
                 WHEN a.aggregate_type='TENANT_VERIFICATION' THEN 'REVIEW'
                 WHEN a.aggregate_type='TENANT_PROVISIONING' THEN 'PROVISIONING'
                 WHEN a.aggregate_type='PLANNING_FLOW' THEN 'AUTOMATION'
                 WHEN a.aggregate_type IN ('DELIVERY_ZONE','TENANT_DELIVERY_ZONE') THEN 'CONFIGURATION' ELSE 'OTHER' END
            """;
    private static final String SEVERITY = """
            CASE WHEN a.action IN ('FAILED','PROVISIONING_FAILED') THEN 'ERROR'
                 WHEN a.action IN ('TENANT_SUSPENDED','VERIFICATION_REJECTED','VERIFICATION_CHANGES_REQUIRED') THEN 'WARNING'
                 ELSE 'INFO' END
            """;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot snapshot() {
        return new Snapshot(Instant.now(), history(new Filter("", "", "", 7, null, null), 100), overview());
    }

    public AuditPage history(Filter filter, int size) {
        if (size < 1 || size > 100) throw new AppException(ErrorCode.VALIDATION_ERROR);
        StringBuilder sql = new StringBuilder(COLUMNS).append(" WHERE a.created_at >= ?");
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(Instant.now().minus(filter.days(), ChronoUnit.DAYS)));
        if (!filter.query().isEmpty()) {
            sql.append(" AND strpos(lower(concat_ws(' ',a.action,a.aggregate_type,t.business_name,a.actor_id::text,a.tenant_id::text)),lower(?))>0");
            args.add(filter.query());
        }
        if (!filter.category().isEmpty()) { sql.append(" AND (").append(CATEGORY).append(")=?"); args.add(filter.category()); }
        if (!filter.severity().isEmpty()) { sql.append(" AND (").append(SEVERITY).append(")=?"); args.add(filter.severity()); }
        if (filter.beforeTime() != null) {
            sql.append(" AND (a.created_at,a.audit_id)<(?,?)");
            args.add(Timestamp.from(filter.beforeTime())); args.add(filter.beforeId());
        }
        sql.append(" ORDER BY a.created_at DESC,a.audit_id DESC LIMIT ?"); args.add(size + 1);
        var rows = jdbc.query(sql.toString(), this::entry, args.toArray());
        return new AuditPage(List.copyOf(rows.subList(0, Math.min(size, rows.size()))), rows.size() > size);
    }

    public AuditEntry find(UUID id) {
        var rows = jdbc.query(COLUMNS + " WHERE a.audit_id=?", this::entry, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> overview() {
        var statuses = jdbc.queryForList("SELECT status,count(*) AS total FROM tenants GROUP BY status ORDER BY status");
        var waiting = jdbc.queryForList("""
                SELECT r.request_id,r.tenant_id,t.business_name,r.status,r.submitted_at,
                       greatest(0,floor(extract(epoch FROM (now()-coalesce(r.submitted_at,r.created_at)))/3600))::int AS waiting_hours
                FROM tenant_verification_requests r JOIN tenants t USING(tenant_id)
                WHERE r.status IN ('SUBMITTED','UNDER_REVIEW') AND r.closed_at IS NULL
                ORDER BY coalesce(r.submitted_at,r.created_at),r.request_id LIMIT 6
                """);
        Long pending = jdbc.queryForObject("SELECT count(*) FROM tenant_verification_requests WHERE status IN ('SUBMITTED','UNDER_REVIEW') AND closed_at IS NULL", Long.class);
        var provisioning = jdbc.queryForList("""
                SELECT p.tenant_id,t.business_name,p.status,p.attempts,p.last_error_code,p.updated_at
                FROM tenant_provisioning p JOIN tenants t USING(tenant_id) WHERE p.status='RETRY_REQUIRED'
                ORDER BY p.updated_at LIMIT 6
                """);
        Long retry = jdbc.queryForObject("SELECT count(*) FROM tenant_provisioning WHERE status='RETRY_REQUIRED'", Long.class);
        var activity = jdbc.queryForList("""
                SELECT to_char(day,'YYYY-MM-DD') AS day,coalesce(counts.total,0) AS total
                FROM generate_series((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date-6,
                                     (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,interval '1 day') day
                LEFT JOIN (SELECT (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day,count(*) AS total
                           FROM audit_logs WHERE created_at>=((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date-6) AT TIME ZONE 'Asia/Ho_Chi_Minh'
                           GROUP BY 1) counts USING(day) ORDER BY day
                """);
        return Map.of("tenant_statuses", statuses, "pending_reviews", pending, "review_priority", waiting,
                "provisioning_retries", retry, "provisioning_attention", provisioning, "activity_days", activity);
    }

    private AuditEntry entry(ResultSet row, int index) throws SQLException {
        String action = row.getString("action"), type = row.getString("aggregate_type");
        return new AuditEntry(row.getObject("audit_id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getString("tenant_name"), type, row.getObject("aggregate_id", UUID.class), action,
                row.getObject("actor_id", UUID.class), row.getTimestamp("created_at").toInstant(), category(type, action),
                severity(action), safeDetails(row.getString("details")));
    }

    Map<String, Object> safeDetails(String raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var details = json.readTree(raw);
            for (String key : List.of("reason", "status", "previous_status", "outcome", "source", "scheduled_for",
                    "revision", "eligible_orders", "zone_type", "enforcement_mode", "is_active", "version")) {
                var value = details.path(key);
                if (value.isTextual()) result.put(key, value.asText().substring(0, Math.min(500, value.asText().length()))
                        .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]")
                        .replaceAll("(?i)(password|token|api[_-]?key|secret)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]"));
                else if (value.isBoolean()) result.put(key, value.asBoolean());
                else if (value.isNumber()) result.put(key, value.numberValue());
            }
        } catch (Exception ignored) { }
        return Map.copyOf(result);
    }

    public static String category(String type, String action) {
        if (Set.of("TENANT_SUSPENDED", "TENANT_REACTIVATED").contains(action)) return "ACCESS";
        return switch (type) {
            case "TENANT_VERIFICATION" -> "REVIEW";
            case "TENANT_PROVISIONING" -> "PROVISIONING";
            case "PLANNING_FLOW" -> "AUTOMATION";
            case "DELIVERY_ZONE", "TENANT_DELIVERY_ZONE" -> "CONFIGURATION";
            default -> "OTHER";
        };
    }
    public static String severity(String action) {
        if (Set.of("FAILED", "PROVISIONING_FAILED").contains(action)) return "ERROR";
        return Set.of("TENANT_SUSPENDED", "VERIFICATION_REJECTED", "VERIFICATION_CHANGES_REQUIRED").contains(action) ? "WARNING" : "INFO";
    }
}
