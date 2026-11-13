package com.swiftroute.notificationservice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class DispatchAlertController {
    private final NotificationAuthorization authorization;
    private final NotificationSseService sseService;
    private final JdbcTemplate jdbc;

    public record DispatchAlert(UUID id, String incidentId, String driverId, String dispatchPlanId,
            String orderId, String title, String body, String severity, String actionUrl, Instant createdAt) {}

    public record InboxNotification(UUID id, String templateCode, String category, String severity,
            String title, String body, String actionUrl, String incidentId, String orderId,
            String dispatchPlanId, Instant createdAt) {}

    @GetMapping("/dispatch-alerts")
    public Map<String, Object> alerts(@RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantIdHeader) {
        UUID tenantId = authorization.resolveOperatorTenantId(bearer, tenantIdHeader);
        List<DispatchAlert> alerts = jdbc.query("""
                SELECT n.delivery_id, n.title, n.body, n.created_at,
                    n.payload->>'incident_id' AS incident_id,
                    n.payload->>'driver_id' AS driver_id,
                    n.payload->>'dispatch_plan_id' AS dispatch_plan_id,
                    n.payload->>'order_id' AS order_id,
                    COALESCE(n.payload->>'severity', 'MEDIUM') AS severity
                FROM notification_deliveries n
                WHERE n.tenant_id = ? AND n.channel = 'IN_APP'
                  AND n.template_code = 'delivery.incident'
                  AND n.payload->>'incident_id' IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM notification_deliveries r
                    WHERE r.tenant_id = n.tenant_id AND r.template_code = 'incident.resolved'
                      AND r.payload->>'incident_id' = n.payload->>'incident_id'
                      AND r.payload->>'status' IN ('RESOLVED', 'CANCELLED')
                  )
                ORDER BY CASE n.payload->>'severity'
                    WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC,
                    n.created_at DESC
                """, (rs, row) -> new DispatchAlert(rs.getObject("delivery_id", UUID.class),
                    rs.getString("incident_id"), rs.getString("driver_id"), rs.getString("dispatch_plan_id"),
                    rs.getString("order_id"), rs.getString("title"), rs.getString("body"), rs.getString("severity"),
                    "/operations?incident=" + rs.getString("incident_id"), rs.getTimestamp("created_at").toInstant()), tenantId);
        return Map.of("data", alerts, "timestamp", Instant.now());
    }

    @GetMapping("/inbox")
    public Map<String, Object> tenantInbox(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantIdHeader) {
        UUID tenantId = authorization.resolveOperatorTenantId(bearer, tenantIdHeader);
        List<InboxNotification> items = jdbc.query("""
                SELECT n.delivery_id, n.template_code, n.title, n.body, n.created_at,
                    COALESCE(n.payload->>'incident_id', n.payload->>'incidentId') AS incident_id,
                    COALESCE(n.payload->>'order_id', n.payload->>'orderId') AS order_id,
                    COALESCE(n.payload->>'dispatch_plan_id', n.payload->>'plan_id', n.payload->>'planId') AS dispatch_plan_id,
                    COALESCE(n.payload->>'severity', '') AS raw_severity
                FROM notification_deliveries n
                WHERE n.tenant_id = ?
                  AND n.channel = 'IN_APP'
                  AND COALESCE(n.template_code, '') NOT IN (
                      'user.email_verification.v1',
                      'tenant.driver_invited.v1',
                      'tenant.created.v1',
                      'tenant.verification_submitted.v1',
                      'assignment.timeout',
                      'assignment.escalated'
                  )
                ORDER BY n.created_at DESC
                LIMIT 40
                """, (rs, row) -> {
                    String templateCode = rs.getString("template_code");
                    String incidentId = rs.getString("incident_id");
                    String orderId = rs.getString("order_id");
                    String planId = rs.getString("dispatch_plan_id");
                    String rawSeverity = rs.getString("raw_severity");
                    String severity = (rawSeverity != null && !rawSeverity.isBlank())
                            ? rawSeverity
                            : OperationalAlertConsumer.resolveSeverity(templateCode, null);
                    return new InboxNotification(
                            rs.getObject("delivery_id", UUID.class),
                            templateCode,
                            OperationalAlertConsumer.resolveCategory(templateCode),
                            severity,
                            rs.getString("title"),
                            rs.getString("body"),
                            OperationalAlertConsumer.resolveActionUrl(templateCode, incidentId, orderId, planId),
                            incidentId,
                            orderId,
                            planId,
                            rs.getTimestamp("created_at").toInstant());
                }, tenantId);
        return Map.of("data", items, "timestamp", Instant.now());
    }

    @GetMapping("/driver-inbox")
    public Map<String, Object> driverInbox(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantIdHeader) {
        NotificationAuthorization.AuthorizedContext context = authorization.resolveMember(bearer, tenantIdHeader);
        UUID tenantId = context.tenantId();
        UUID driverUserId = context.userId();
        String driverText = driverUserId.toString();
        List<InboxNotification> items = jdbc.query("""
                SELECT n.delivery_id, n.template_code, n.title, n.body, n.created_at,
                    COALESCE(n.payload->>'incident_id', n.payload->>'incidentId') AS incident_id,
                    COALESCE(n.payload->>'order_id', n.payload->>'orderId') AS order_id,
                    COALESCE(n.payload->>'dispatch_plan_id', n.payload->>'plan_id', n.payload->>'planId') AS dispatch_plan_id,
                    COALESCE(n.payload->>'severity', '') AS raw_severity
                FROM notification_deliveries n
                WHERE n.tenant_id = ?
                  AND n.template_code IN (
                      'plan.dispatched',
                      'route.updated',
                      'plan.cancelled',
                      'incident.resolved',
                      'return.approved',
                      'cod.handover_verified',
                      'cod.handover_discrepancy',
                      'cod.dispute_resolved',
                      'settlement.approved',
                      'settlement.paid',
                      'driver.profile_reviewed'
                  )
                  AND (
                      n.user_id = ?
                      OR n.payload->>'driver_id' = ?
                      OR n.payload->>'driverId' = ?
                      OR n.payload->>'assigned_driver_id' = ?
                      OR n.payload->>'assignedDriverId' = ?
                  )
                ORDER BY n.created_at DESC
                LIMIT 40
                """, (rs, row) -> {
                    String templateCode = rs.getString("template_code");
                    String incidentId = rs.getString("incident_id");
                    String orderId = rs.getString("order_id");
                    String planId = rs.getString("dispatch_plan_id");
                    String rawSeverity = rs.getString("raw_severity");
                    String severity = (rawSeverity != null && !rawSeverity.isBlank())
                            ? rawSeverity
                            : OperationalAlertConsumer.resolveSeverity(templateCode, null);
                    return new InboxNotification(
                            rs.getObject("delivery_id", UUID.class),
                            templateCode,
                            OperationalAlertConsumer.resolveCategory(templateCode),
                            severity,
                            rs.getString("title"),
                            rs.getString("body"),
                            OperationalAlertConsumer.resolveActionUrl(templateCode, incidentId, orderId, planId),
                            incidentId,
                            orderId,
                            planId,
                            rs.getTimestamp("created_at").toInstant());
                }, tenantId, driverUserId, driverText, driverText, driverText, driverText);
        return Map.of("data", items, "timestamp", Instant.now());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantIdHeader) {
        UUID tenantId = authorization.resolveOperatorTenantId(bearer, tenantIdHeader);
        return sseService.subscribe(tenantId);
    }
}
