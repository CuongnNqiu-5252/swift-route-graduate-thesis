package com.swiftroute.orderservice.service.planning;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.PlanTriggerType;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled component that periodically evaluates whether any active tenant's
 * pending order queue should trigger a new planning cycle.
 *
 * <p>
 * Trigger types evaluated:
 * <ul>
 * <li>URGENT — an URGENT-priority order is waiting (cadence tick)</li>
 * <li>BATCH_SIZE — pending orders reached the configured threshold (cadence
 * tick)</li>
 * <li>MAX_WAIT — oldest pending order has waited too long (cadence tick)</li>
 * <li>SCHEDULED — a published Operation Flow slot is due</li>
 * <li>MANUAL — triggered explicitly by operator via Web Admin</li>
 * </ul>
 *
 * <p>
 * <strong>Duplicate prevention</strong>: each invocation acquires a
 * per-tenant PostgreSQL transaction advisory lock before
 * starting a planning cycle so that concurrent scheduler instances in a
 * multi-node deployment do not trigger duplicate plans for the same tenant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanningTriggerScheduler {

    private final TenantRepository tenantRepository;
    private final PlanningEligibilityEvaluator eligibilityEvaluator;
    private final DispatchOrchestrationService orchestrationService;
    private final AdvisoryLockHelper advisoryLockHelper;
    private final PlanningFlowScheduleService planningSchedule;
    private final TransactionTemplate transactions;

    /**
     * Automatic evaluation cadence — runs every 2 minutes.
     * Evaluates dynamic queue conditions (URGENT, BATCH_SIZE, MAX_WAIT) for active
     * tenants.
     */
    @Scheduled(fixedDelayString = "${dispatch.evaluation-interval-ms:120000}")
    public void evaluateAllTenants() {
        List<Tenant> activeTenants = tenantRepository.findAllByStatus(TenantStatus.APPROVED);
        log.debug("Dispatch evaluation tick: {} active tenants.", activeTenants.size());

        for (Tenant tenant : activeTenants) {
            evaluateCadenceForTenant(tenant);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void triggerScheduledFlows() {
        for (UUID tenantId : planningSchedule.dueTenants(Instant.now())) {
            try {
                planningSchedule.runDue(tenantId, Instant.now());
            } catch (PlanningFlowScheduleService.RunFailure failure) {
                log.error("Scheduled planning flow failed for tenant {}", tenantId, failure);
                try {
                    planningSchedule.recordFailure(failure);
                } catch (Exception recordError) {
                    log.error("Could not record scheduled planning failure for tenant {}", tenantId, recordError);
                }
            } catch (Exception e) {
                log.error("Scheduled planning flow failed for tenant {}: {}", tenantId, e.getMessage(), e);
            }
        }
    }

    /**
     * Manual trigger — called by {@code POST /api/orders/dispatch/trigger} from Web
     * Admin.
     * Bypasses eligibility evaluation and fires planning immediately if there are
     * eligible orders.
     *
     * @param tenantId UUID of the tenant to trigger planning for
     * @param pickupLocationId optional depot to restrict planning to
     */
    @Transactional
    public DispatchOrchestrationService.CycleResult triggerManual(UUID tenantId, UUID pickupLocationId) {
        log.info("Manual planning trigger for tenant {}.", tenantId);
        boolean acquired = advisoryLockHelper.tryLock(tenantId.getMostSignificantBits());
        if (!acquired) {
            log.warn("Manual trigger for tenant {} skipped — concurrent planning already in progress.", tenantId);
            return new DispatchOrchestrationService.CycleResult("IN_PROGRESS", 0, List.of());
        }
        return orchestrationService.executeFlowCycle(tenantId, PlanTriggerType.MANUAL, pickupLocationId);
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    /**
     * Evaluates dynamic queue conditions (URGENT, BATCH_SIZE, MAX_WAIT) for cadence
     * ticks.
     */
    private void evaluateCadenceForTenant(Tenant tenant) {
        UUID tenantId = tenant.getId();
        try {
            Optional<PlanTriggerType> trigger = eligibilityEvaluator.evaluate(tenant, Instant.now());
            if (trigger.isEmpty()) {
                return; // No dynamic trigger condition met
            }

            transactions.executeWithoutResult(status -> {
                if (!advisoryLockHelper.tryLock(tenantId.getMostSignificantBits()))
                    return;
                orchestrationService.executePlanningCycle(tenantId, trigger.get());
            });
        } catch (Exception e) {
            log.error("Planning cadence evaluation failed for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }
}
