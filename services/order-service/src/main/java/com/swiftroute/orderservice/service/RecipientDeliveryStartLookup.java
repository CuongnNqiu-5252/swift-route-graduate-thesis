package com.swiftroute.orderservice.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecipientDeliveryStartLookup {
    private final JdbcTemplate jdbc;

    public Optional<Details> findCurrent(UUID tenantId, UUID eventId) {
        return jdbc.query("""
                SELECT o.recipient_phone, o.tracking_token, o.external_order_code,
                       o.order_id, t.business_name, o.time_window_start, o.time_window_end, e.event_type
                FROM outbox_events e
                JOIN delivery_tasks d ON d.task_id = e.aggregate_id
                JOIN orders o ON o.order_id = d.order_id AND o.tenant_id = d.tenant_id
                JOIN tenants t ON t.id = o.tenant_id
                WHERE e.event_id = ? AND o.tenant_id = ?
                  AND e.payload->>'order_id' = CAST(o.order_id AS text) AND d.task_type = 'DELIVERY'
                  AND (
                    (e.event_type = 'order.delivery_started.v1'
                        AND d.status = 'OUT_FOR_DELIVERY' AND o.status = 'IN_TRANSIT'
                        AND o.assigned_driver_id = d.driver_id
                        AND (d.dispatch_plan_id IS NULL OR o.plan_id = d.dispatch_plan_id)
                        AND NOT EXISTS (
                            SELECT 1 FROM delivery_attempts a
                            WHERE a.order_id = o.order_id AND a.tenant_id = o.tenant_id
                              AND a.completed_at >= CAST(e.payload->>'started_at' AS timestamptz)
                        ))
                    OR EXISTS (
                        SELECT 1 FROM delivery_attempts a
                        WHERE a.order_id = o.order_id AND a.tenant_id = o.tenant_id
                          AND a.task_id = d.task_id
                          AND CAST(a.attempt_number AS text) = e.payload->>'attempt_number'
                          AND ((e.event_type = 'order.recipient_delivery_completed.v1'
                                AND a.outcome = 'SUCCESS' AND o.status = 'DELIVERED')
                            OR (e.event_type = 'order.recipient_delivery_failed.v1'
                                AND a.outcome = 'FAILED' AND o.status = 'FAILED'
                                AND NOT EXISTS (
                                    SELECT 1 FROM delivery_attempts newer
                                    WHERE newer.order_id = o.order_id AND newer.tenant_id = o.tenant_id
                                      AND newer.attempt_number > a.attempt_number
                                )))
                    )
                  )
                """, (rs, row) -> {
            String code = rs.getString("external_order_code");
            if (code == null || code.isBlank()) {
                code = "#" + rs.getObject("order_id", UUID.class).toString().substring(0, 8)
                        .toUpperCase(java.util.Locale.ROOT);
            }
            return new Details(rs.getString("recipient_phone"), rs.getString("tracking_token"), code.trim(),
                    rs.getString("business_name"), rs.getObject("time_window_start", OffsetDateTime.class),
                    rs.getObject("time_window_end", OffsetDateTime.class), rs.getString("event_type"));
        }, eventId, tenantId).stream().findFirst();
    }

    public record Details(String recipientPhone, String trackingToken, String orderCode, String carrierName,
                          OffsetDateTime timeWindowStart, OffsetDateTime timeWindowEnd, String eventType) {}
}
