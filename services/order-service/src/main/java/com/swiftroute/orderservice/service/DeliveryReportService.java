package com.swiftroute.orderservice.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.response.DeliveryReportResponse;
import com.swiftroute.orderservice.dto.response.DeliveryReportResponse.Day;
import com.swiftroute.orderservice.dto.response.DeliveryReportResponse.Detail;
import com.swiftroute.orderservice.dto.response.DeliveryReportResponse.Failure;
import com.swiftroute.orderservice.dto.response.DeliveryReportResponse.Warehouse;
import com.swiftroute.orderservice.dto.response.PageResponse;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DeliveryReportService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_DAYS = 90;
    private static final int PAGE_SIZE = 20;
    private static final String ORDER_SCOPE = """
            o.tenant_id = :tenant AND (CAST(:warehouse AS uuid) IS NULL OR o.pickup_location_id = :warehouse)
            """;
    private static final String ATTEMPTS = """
            WITH attempts AS (
                SELECT a.attempt_id, a.order_id, a.attempt_number, a.outcome, a.completed_at,
                    o.time_window_end AS deadline,
                    COALESCE(NULLIF(o.external_order_code, ''), o.tracking_token) AS order_code,
                    CAST(a.completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS day,
                    CASE WHEN a.outcome = 'FAILED'
                         THEN COALESCE(NULLIF(UPPER(BTRIM(a.failure_reason)), ''), 'UNSPECIFIED')
                    END AS reason,
                    a.outcome = 'SUCCESS' AND NOT EXISTS (
                        SELECT 1 FROM delivery_attempts earlier
                        WHERE earlier.order_id = a.order_id AND earlier.tenant_id = a.tenant_id
                          AND earlier.attempt_number < a.attempt_number AND earlier.outcome = 'SUCCESS'
                    ) AS delivered
                FROM delivery_attempts a
                JOIN orders o ON o.order_id = a.order_id AND o.tenant_id = a.tenant_id
                WHERE a.completed_at >= :start AND a.completed_at < :end AND
            """ + ORDER_SCOPE + """
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DeliveryReportResponse overview(UUID tenant, UUID warehouse, LocalDate from, LocalDate to) {
        var params = parameters(tenant, warehouse, from, to);
        var days = jdbc.query(ATTEMPTS + """
                SELECT day,
                    COUNT(*) FILTER (WHERE delivered) AS delivered,
                    COUNT(*) FILTER (WHERE delivered AND deadline IS NOT NULL) AS with_deadline,
                    COUNT(*) FILTER (WHERE delivered AND completed_at <= deadline) AS on_time,
                    COUNT(*) FILTER (WHERE attempt_number = 1) AS first_attempts,
                    COUNT(*) FILTER (WHERE attempt_number = 1 AND delivered) AS first_successes,
                    COUNT(reason) AS failures
                FROM attempts GROUP BY day ORDER BY day
                """, params, (rs, row) -> new Day(rs.getObject("day", LocalDate.class), rs.getLong("delivered"),
                rs.getLong("with_deadline"), rs.getLong("on_time"), rs.getLong("first_attempts"),
                rs.getLong("first_successes"), rs.getLong("failures")));

        var failures = jdbc.query(ATTEMPTS + """
                SELECT reason, COUNT(*) AS count FROM attempts WHERE reason IS NOT NULL
                GROUP BY reason ORDER BY count DESC, reason
                """, params, (rs, row) -> new Failure(rs.getString("reason"), rs.getLong("count")));

        long overdue = Objects.requireNonNull(jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders o
                WHERE o.time_window_end >= :start AND o.time_window_end < :end
                  AND o.time_window_end < CURRENT_TIMESTAMP
                  AND o.status IN ('READY_FOR_DISPATCH', 'PLANNED', 'IN_TRANSIT', 'FAILED')
                  AND
                """ + ORDER_SCOPE, params, Long.class));

        var warehouses = jdbc.query("""
                SELECT id, name FROM pickup_locations WHERE tenant_id = :tenant ORDER BY name, id
                """, params, (rs, row) -> new Warehouse(rs.getObject("id", UUID.class), rs.getString("name")));

        return new DeliveryReportResponse(days, failures, overdue, warehouses);
    }

    public PageResponse<Detail> details(UUID tenant, UUID warehouse, LocalDate from, LocalDate to,
                                       DetailKind kind, String reason, int page) {
        if (page < 0 || (kind == DetailKind.FAILURE && (reason == null || reason.isBlank() || reason.length() > 100))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        var params = parameters(tenant, warehouse, from, to)
                .addValue("reason", reason)
                .addValue("offset", (long) page * PAGE_SIZE)
                .addValue("limit", PAGE_SIZE);
        String where = kind == DetailKind.LATE ? "delivered AND completed_at > deadline" : "reason = :reason";
        long total = Objects.requireNonNull(jdbc.queryForObject(ATTEMPTS + "SELECT COUNT(*) FROM attempts WHERE " + where,
                params, Long.class));
        List<Detail> rows = jdbc.query(ATTEMPTS + """
                SELECT order_id, order_code, completed_at, deadline, attempt_number
                FROM attempts WHERE
                """ + where + " ORDER BY completed_at DESC, attempt_id LIMIT :limit OFFSET :offset",
                params, (rs, row) -> new Detail(rs.getObject("order_id", UUID.class), rs.getString("order_code"),
                rs.getObject("completed_at", OffsetDateTime.class), rs.getObject("deadline", OffsetDateTime.class),
                rs.getInt("attempt_number")));
        return new PageResponse<>(rows, page, PAGE_SIZE, total, Math.toIntExact(Math.ceilDiv(total, PAGE_SIZE)),
                page == 0, (page + 1L) * PAGE_SIZE >= total);
    }

    private MapSqlParameterSource parameters(UUID tenant, UUID warehouse, LocalDate from, LocalDate to) {
        if (from.isBefore(LocalDate.of(1, 1, 1)) || to.isAfter(LocalDate.of(9999, 12, 30))
                || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS
                || to.isAfter(LocalDate.now(REPORT_ZONE))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return new MapSqlParameterSource("tenant", tenant)
                .addValue("warehouse", warehouse, java.sql.Types.OTHER)
                .addValue("start", from.atStartOfDay(REPORT_ZONE).toOffsetDateTime())
                .addValue("end", to.plusDays(1).atStartOfDay(REPORT_ZONE).toOffsetDateTime());
    }

    public enum DetailKind { LATE, FAILURE }
}
