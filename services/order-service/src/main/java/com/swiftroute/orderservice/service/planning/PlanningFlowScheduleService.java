package com.swiftroute.orderservice.service.planning;

import com.swiftroute.orderservice.dto.OperationFlowSchedule;
import com.swiftroute.orderservice.entity.enums.PlanTriggerType;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanningFlowScheduleService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AdvisoryLockHelper locks;
    private final DispatchOrchestrationService orchestration;

    public record State(OperationFlowSchedule schedule, Integer revision, Instant nextRunAt, UUID pickupLocationId) {}
    private record StoredSchedule(String mode, String time, int hours,
            @JsonProperty("pickup_location_id") UUID pickupLocationId) {}
    private record RunDetails(String source, String outcome, @JsonProperty("scheduled_for") String scheduledFor, Integer revision,
            UUID pickupLocationId, int eligibleOrders, List<UUID> planIds) {}
    public record Run(UUID id, Instant startedAt, String source, String outcome, String scheduledFor,
            Integer revision, UUID pickupLocationId, int eligibleOrders, List<UUID> planIds) {}
    public record RunPage(List<Run> content, boolean hasMore) {}

    public static final class RunFailure extends IllegalStateException {
        private final UUID tenant;
        private final UUID actor;
        private final Instant startedAt;
        private final RunDetails details;

        private RunFailure(UUID tenant, UUID actor, Instant startedAt, RunDetails details, RuntimeException cause) {
            super("Operation Flow planning failed", cause);
            this.tenant = tenant;
            this.actor = actor;
            this.startedAt = startedAt;
            this.details = details;
        }
    }

    private RowMapper<State> scheduleState() {
        return (rs, index) -> {
            var stored = json.readValue(rs.getString("active_schedule"), StoredSchedule.class);
            return new State(new OperationFlowSchedule(stored.mode(), stored.time(), stored.hours()),
                    rs.getObject("active_revision", Integer.class), rs.getTimestamp("next_run_at").toInstant(),
                    stored.pickupLocationId());
        };
    }

    public State read(UUID tenant) {
        return jdbc.query("""
                SELECT active_schedule, active_revision, next_run_at
                FROM operation_flow_runtime
                WHERE tenant_id = ? AND flow_kind = 'planning' AND next_run_at IS NOT NULL
                """, scheduleState(), tenant).stream().findFirst().orElse(null);
    }

    public State activate(UUID tenant, int revision, OperationFlowSchedule schedule, UUID pickupLocationId) {
        String value = json.writeValueAsString(new StoredSchedule(schedule.mode(), schedule.time(),
                schedule.hours(), pickupLocationId));
        var states = jdbc.query("""
                UPDATE operation_flow_runtime
                SET next_run_at = CASE WHEN active_schedule->>'mode' = ?
                    AND ((? = 'daily' AND active_schedule->>'time' = ?)
                         OR (? = 'interval' AND active_schedule->>'hours' = ?))
                    THEN next_run_at ELSE ? END,
                    active_schedule = CAST(? AS jsonb), active_revision = ?
                WHERE tenant_id = ? AND flow_kind = 'planning' AND revision = ?
                RETURNING active_schedule, active_revision, next_run_at
                """, scheduleState(), schedule.mode(), schedule.mode(), schedule.time(), schedule.mode(),
                Integer.toString(schedule.hours()), Timestamp.from(schedule.nextRunAfter(Instant.now(), null)),
                value, revision, tenant, revision);
        return states.stream().findFirst().orElseThrow(() -> new AppException(ErrorCode.CONCURRENT_UPDATE));
    }

    public void pause(UUID tenant) {
        jdbc.update("""
                UPDATE operation_flow_runtime
                SET active_schedule = NULL, active_revision = NULL, next_run_at = NULL
                WHERE tenant_id = ? AND flow_kind = 'planning'
                """, tenant);
    }

    public List<UUID> dueTenants(Instant now) {
        return jdbc.queryForList("""
                SELECT flow.tenant_id
                FROM operation_flow_runtime flow
                JOIN tenants ON tenants.tenant_id = flow.tenant_id
                WHERE flow.flow_kind = 'planning' AND flow.next_run_at <= ? AND tenants.status = 'APPROVED'
                ORDER BY flow.next_run_at
                """, UUID.class, Timestamp.from(now));
    }

    @Transactional
    public void runDue(UUID tenant, Instant now) {
        if (!locks.tryLock(tenant.getMostSignificantBits())) return;

        var due = jdbc.query("""
                SELECT active_schedule, active_revision, next_run_at
                FROM operation_flow_runtime flow
                JOIN tenants ON tenants.tenant_id = flow.tenant_id
                WHERE flow.tenant_id = ? AND flow.flow_kind = 'planning'
                    AND flow.next_run_at <= ? AND tenants.status = 'APPROVED'
                FOR UPDATE OF flow
                """, scheduleState(), tenant, Timestamp.from(now));
        if (due.isEmpty()) return;

        State state = due.getFirst();
        run(tenant, null, PlanTriggerType.SCHEDULED, now, state.nextRunAt(), state.revision(), state.pickupLocationId());
        jdbc.update("""
                UPDATE operation_flow_runtime SET next_run_at = ?
                WHERE tenant_id = ? AND flow_kind = 'planning'
                """, Timestamp.from(state.schedule().nextRunAfter(now, state.nextRunAt())), tenant);
    }

    @Transactional
    public Run runManual(UUID tenant, UUID actor, UUID pickupLocationId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM tenants WHERE tenant_id = ? AND status = 'APPROVED')
                """, Boolean.class, tenant))) {
            throw new AppException(ErrorCode.OPERATIONS_NOT_READY);
        }
        if (!locks.tryLock(tenant.getMostSignificantBits())) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }
        return run(tenant, actor, PlanTriggerType.MANUAL, Instant.now(), null, null, pickupLocationId);
    }

    public boolean isActivePickup(UUID tenant, UUID pickupLocationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pickup_locations WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE')
                """, Boolean.class, tenant, pickupLocationId));
    }

    private Run run(UUID tenant, UUID actor, PlanTriggerType type, Instant now, Instant slot,
            Integer revision, UUID pickupLocationId) {
        String scheduledFor = slot == null ? null : slot.toString();
        try {
            if (pickupLocationId != null && !isActivePickup(tenant, pickupLocationId)) {
                return record(tenant, actor, now, new RunDetails(type.name(), "PICKUP_UNAVAILABLE",
                        scheduledFor, revision, pickupLocationId, 0, List.of()));
            }
            var result = orchestration.executeFlowCycle(tenant, type, pickupLocationId);
            return record(tenant, actor, now, new RunDetails(type.name(), result.outcome(), scheduledFor,
                    revision, pickupLocationId, result.eligibleOrders(),
                    result.plans().stream().map(plan -> plan.getId()).toList()));
        } catch (RuntimeException failure) {
            throw new RunFailure(tenant, actor, now, new RunDetails(type.name(), "FAILED",
                    scheduledFor, revision, pickupLocationId, 0, List.of()), failure);
        }
    }

    @Transactional
    public Run recordFailure(RunFailure failure) {
        RunDetails details = failure.details;
        if (failure.actor != null) log.error("Manual planning flow failed for tenant {}", failure.tenant, failure);
        if (details.scheduledFor() != null) {
            if (!locks.tryLock(failure.tenant.getMostSignificantBits())) return null;
            boolean alreadyRecorded = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM audit_logs WHERE tenant_id = ? AND aggregate_type = 'PLANNING_FLOW'
                        AND aggregate_id = ? AND action = 'FAILED' AND details->>'scheduled_for' = ?)
                    """, Boolean.class, failure.tenant, failure.tenant, details.scheduledFor()));
            if (alreadyRecorded) return null;
        }
        return record(failure.tenant, failure.actor, failure.startedAt, details);
    }

    private Run record(UUID tenant, UUID actor, Instant startedAt, RunDetails details) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_logs (audit_id, tenant_id, aggregate_type, aggregate_id, action, actor_id, details, created_at)
                VALUES (?, ?, 'PLANNING_FLOW', ?, ?, ?, CAST(? AS jsonb), ?)
                """, id, tenant, tenant, details.outcome(), actor, json.writeValueAsString(details), Timestamp.from(startedAt));
        return toRun(id, startedAt, details);
    }

    public RunPage history(UUID tenant, int page) {
        int size = 20;
        var runs = jdbc.query("""
                SELECT audit_id, created_at, details FROM audit_logs
                WHERE tenant_id = ? AND aggregate_type = 'PLANNING_FLOW' AND aggregate_id = ?
                ORDER BY created_at DESC, audit_id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> toRun(rs.getObject("audit_id", UUID.class), rs.getTimestamp("created_at").toInstant(),
                json.readValue(rs.getString("details"), RunDetails.class)), tenant, tenant, size + 1, (long) page * size);
        return new RunPage(runs.subList(0, Math.min(size, runs.size())), runs.size() > size);
    }

    private Run toRun(UUID id, Instant startedAt, RunDetails details) {
        return new Run(id, startedAt, details.source(), details.outcome(), details.scheduledFor(),
                details.revision(), details.pickupLocationId(), details.eligibleOrders(), details.planIds());
    }
}
