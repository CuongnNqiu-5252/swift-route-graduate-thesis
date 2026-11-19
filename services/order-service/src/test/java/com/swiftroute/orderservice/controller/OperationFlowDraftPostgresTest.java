package com.swiftroute.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftroute.orderservice.controller.OperationFlowDraftController.Activation;
import com.swiftroute.orderservice.controller.OperationFlowDraftController.Draft;
import com.swiftroute.orderservice.controller.OperationFlowDraftController.Link;
import com.swiftroute.orderservice.controller.OperationFlowDraftController.Position;
import com.swiftroute.orderservice.controller.OperationFlowDraftController.Step;
import com.swiftroute.orderservice.controller.OperationFlowDraftController.Version;
import com.swiftroute.orderservice.dto.OperationFlowSchedule;
import com.swiftroute.orderservice.entity.enums.PlanTriggerType;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.service.planning.AdvisoryLockHelper;
import com.swiftroute.orderservice.service.planning.DispatchOrchestrationService;
import com.swiftroute.orderservice.service.planning.PlanningFlowScheduleService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "OPERATION_FLOW_TEST_URL", matches = ".+")
class OperationFlowDraftPostgresTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private DispatchOrchestrationService orchestration;
    private PlanningFlowScheduleService planning;
    private OperationFlowDraftController controller;
    private UUID tenant;

    @BeforeEach
    void setUp() {
        var db = new DriverManagerDataSource(System.getenv("OPERATION_FLOW_TEST_URL"),
                System.getenv("OPERATION_FLOW_TEST_USER"), System.getenv("OPERATION_FLOW_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(db);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(db));
        orchestration = mock(DispatchOrchestrationService.class);
        var json = JsonMapper.builder().build();
        planning = new PlanningFlowScheduleService(jdbc, json, new AdvisoryLockHelper(jdbc), orchestration);
        controller = new OperationFlowDraftController(jdbc, json, planning);
        tenant = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (tenant_id, business_name, business_type, created_at, optimization_weights,
                    owner_user_id, status, status_updated_at, subscription_plan, version)
                VALUES (?, 'Schedule test', 'OTHER', now(), '{}'::jsonb, ?, 'APPROVED', now(), 'FREE_TRIAL', 0)
                """, tenant, UUID.randomUUID());
    }

    @Test
    void savesDraftByTenantAndRejectsStaleUpdates() {
        var draft = new Draft(List.of(new Step("schedule", "schedule", new Position(0, 64))),
                List.of(), new OperationFlowSchedule("daily", "06:00", 3), true, false);
        var changed = new Draft(draft.nodes(), draft.edges(), new OperationFlowSchedule("daily", "08:45", 3), false, true);

        assertThat(controller.read("import", tenant, "OWNER").data()).isNull();
        assertThat(controller.save("import", new Version(0, draft), tenant, "OWNER").data().revision()).isEqualTo(1);
        assertThat(controller.read("import", tenant, "OWNER").data().draft()).isEqualTo(draft);
        assertThat(controller.read("import", UUID.randomUUID(), "OWNER").data()).isNull();
        assertThat(controller.read("planning", tenant, "OWNER").data()).isNull();
        assertThat(controller.save("import", new Version(1, changed), tenant, "OWNER").data())
                .isEqualTo(new Version(2, changed));
        assertThatThrownBy(() -> controller.save("import", new Version(1, draft), tenant, "OWNER"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONCURRENT_UPDATE);
        assertThat(controller.read("import", tenant, "OWNER").data().draft()).isEqualTo(changed);
    }

    @Test
    void publishesOnlyCompleteSavedPlanningDraftAndPausesTheActiveSchedule() {
        var schedule = new OperationFlowSchedule("daily", "08:45", 3);
        var draft = planningDraft(schedule);
        controller.save("planning", new Version(0, new Draft(draft.nodes(), List.of(), schedule, true, false)), tenant, "OWNER");
        assertThatThrownBy(() -> controller.activatePlanningSchedule(new Activation(1), tenant, "OWNER"))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        controller.save("planning", new Version(1, draft), tenant, "OWNER");
        assertThatThrownBy(() -> controller.activatePlanningSchedule(new Activation(1), tenant, "OWNER"))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.CONCURRENT_UPDATE);

        var active = controller.activatePlanningSchedule(new Activation(2), tenant, "OWNER").data();
        controller.save("planning", new Version(2, planningDraft(new OperationFlowSchedule("interval", "08:45", 2))), tenant, "OWNER");
        assertThat(controller.readPlanningSchedule(tenant, "OWNER").data()).isEqualTo(active);
        assertThat(controller.readPlanningSchedule(UUID.randomUUID(), "OWNER").data()).isNull();
        controller.pausePlanningSchedule(tenant, "OWNER");
        assertThat(controller.readPlanningSchedule(tenant, "OWNER").data()).isNull();
    }

    @Test
    void retriesRolledBackSlotAndConsumesItOnlyOnceAfterSuccess() {
        var schedule = new OperationFlowSchedule("interval", "06:00", 3);
        controller.save("planning", new Version(0, planningDraft(schedule)), tenant, "OWNER");
        controller.activatePlanningSchedule(new Activation(1), tenant, "OWNER");
        Instant slot = Instant.parse("2026-11-16T08:00:00Z");
        Instant tick = slot.plusSeconds(120);
        jdbc.update("UPDATE operation_flow_runtime SET next_run_at = ? WHERE tenant_id = ?", Timestamp.from(slot), tenant);
        doThrow(new IllegalStateException("Planning unavailable"))
                .doReturn(new DispatchOrchestrationService.CycleResult("NO_ORDERS", 0, List.of()))
                .when(orchestration).executeFlowCycle(tenant, PlanTriggerType.SCHEDULED, null);

        assertThat(planning.dueTenants(tick)).contains(tenant);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> planning.runDue(tenant, tick)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(planning.read(tenant).nextRunAt()).isEqualTo(slot);

        transactions.executeWithoutResult(status -> planning.runDue(tenant, tick));
        transactions.executeWithoutResult(status -> planning.runDue(tenant, tick));
        verify(orchestration, times(2)).executeFlowCycle(tenant, PlanTriggerType.SCHEDULED, null);
        assertThat(planning.read(tenant).nextRunAt()).isEqualTo(Instant.parse("2026-11-16T11:00:00Z"));
        assertThat(planning.dueTenants(tick)).doesNotContain(tenant);
    }

    @Test
    @Timeout(10)
    void secondWorkerSkipsSlotWhileTenantPlanningLockIsHeld() {
        controller.save("planning", new Version(0, planningDraft(new OperationFlowSchedule("daily", "06:00", 3))),
                tenant, "OWNER");
        var active = controller.activatePlanningSchedule(new Activation(1), tenant, "OWNER").data();
        var locks = new AdvisoryLockHelper(jdbc);
        when(orchestration.executeFlowCycle(tenant, PlanTriggerType.SCHEDULED, null))
                .thenReturn(new DispatchOrchestrationService.CycleResult("NO_ORDERS", 0, List.of()));

        transactions.executeWithoutResult(status -> {
            assertThat(locks.tryLock(tenant.getMostSignificantBits())).isTrue();
            CompletableFuture.runAsync(() -> transactions.executeWithoutResult(
                    worker -> planning.runDue(tenant, active.nextRunAt()))).join();
            assertThat(planning.read(tenant)).isEqualTo(active);
            verifyNoInteractions(orchestration);
        });
        transactions.executeWithoutResult(status -> planning.runDue(tenant, active.nextRunAt()));
        verify(orchestration).executeFlowCycle(tenant, PlanTriggerType.SCHEDULED, null);
    }

    private Draft planningDraft(OperationFlowSchedule schedule) {
        return new Draft(List.of(new Step("schedule", "schedule", new Position(0, 64)),
                new Step("plan", "plan", new Position(244, 64))),
                List.of(new Link("schedule-plan", "schedule", "plan")), schedule, true, false);
    }
}
