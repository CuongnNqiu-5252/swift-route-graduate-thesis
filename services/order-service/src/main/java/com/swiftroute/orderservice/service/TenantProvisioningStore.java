package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.entity.AuditLog;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.AuditLogRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * Local saga state. Network calls are deliberately outside these transactions.
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningStore {
    private final JdbcTemplate jdbc;
    private final TenantRepository tenants;
    private final OutboxEventRepository events;
    private final AuditLogRepository audit;

    public record State(UUID tenantId, String status, int attempts, Instant updatedAt, String nextAction) {
    }

    public State state(UUID id) {
        var rows = jdbc.query("SELECT status,attempts,updated_at FROM tenant_provisioning WHERE tenant_id=?",
                (rs, n) -> new State(id, rs.getString(1), rs.getInt(2), rs.getTimestamp(3).toInstant(),
                        "READY".equals(rs.getString(1)) ? "BUSINESS_PROFILE" : "WAIT_FOR_WORKSPACE"),
                id);
        if (rows.isEmpty())
            throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
        return rows.getFirst();
    }

    public void requireReady(UUID id) {
        if (!"READY".equals(state(id).status()))
            throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
    }

    @Transactional
    public boolean claim(UUID id) {
        // The lease also limits user-triggered retries; only one caller performs remote
        // provisioning.
        return jdbc.update(
                "UPDATE tenant_provisioning SET attempts=attempts+1,next_attempt_at=now()+interval '60 seconds',updated_at=now() WHERE tenant_id=? AND status<>'READY' AND next_attempt_at<=now()",
                id) == 1;
    }

    public List<UUID> due() {
        return jdbc.queryForList(
                "SELECT tenant_id FROM tenant_provisioning WHERE status<>'READY' AND next_attempt_at<=now() ORDER BY next_attempt_at LIMIT 20",
                UUID.class);
    }

    @Transactional
    public void failed(UUID id) {
        jdbc.update(
                "UPDATE tenant_provisioning SET status='RETRY_REQUIRED',last_error_code='OWNER_PROVISIONING_UNAVAILABLE',next_attempt_at=now()+interval '30 seconds',updated_at=now() WHERE tenant_id=? AND status<>'READY'",
                id);
    }

    @Transactional
    public void ready(UUID id) {
        if (jdbc.update(
                "UPDATE tenant_provisioning SET status='READY',ready_at=now(),updated_at=now(),last_error_code=NULL WHERE tenant_id=? AND status<>'READY'",
                id) != 1)
            return;
        Tenant tenant = tenants.findById(id).orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", eventId);
        payload.put("event_type", "tenant.owner_provisioned.v1");
        payload.put("tenant_id", id);
        payload.put("owner_user_id", tenant.getOwnerUserId());
        payload.put("actor_id", tenant.getOwnerUserId());
        payload.put("occurred_at", Instant.now().toString());
        payload.put("payload_version", 1);
        payload.put("version", tenant.getVersion());
        if (tenant.getContactEmail() != null)
            payload.put("email", tenant.getContactEmail());
        events.save(OutboxEvent.builder().eventId(eventId).aggregateId(id).aggregateType("tenant")
                .eventType("tenant.owner_provisioned.v1").payload(payload).build());
        audit.save(AuditLog.builder().tenantId(id).aggregateType("TENANT_PROVISIONING").aggregateId(id)
                .action("OWNER_PROVISIONED").actorId(tenant.getOwnerUserId()).details(Map.of()).build());
    }
}
