package com.swiftroute.orderservice.service.planning;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 * <p>Trigger types evaluated automatically on each cadence tick:
 * <ul>
 *   <li>URGENT — an URGENT-priority order is waiting</li>
 *   <li>BATCH_SIZE — pending orders reached the configured threshold</li>
 *   <li>MAX_WAIT — oldest pending order has waited too long</li>
 * </ul>
 *
 * <p>MANUAL and SCHEDULED triggers are fired externally via
 * {@link #triggerManual(UUID)} and {@link #triggerScheduled()}.
 *
 * <p><strong>Duplicate prevention</strong>: each invocation acquires a
 * per-tenant PostgreSQL advisory lock ({@code pg_try_advisory_lock}) before
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

    /**
     * Automatic evaluation cadence — runs every 2 minutes.
     * Skips evaluation for tenants with {@code auto_dispatch_enabled=false}.
     */
    @Scheduled(fixedDelayString = "${dispatch.evaluation-interval-ms:120000}")
    public void evaluateAllTenants() {
        List<Tenant> activeTenants = tenantRepository.findAllByStatus(TenantStatus.APPROVED);
        log.debug("Dispatch evaluation tick: {} active tenants.", activeTenants.size());

        for (Tenant tenant : activeTenants) {
            evaluateTenant(tenant, PlanTriggerType.BATCH_SIZE); // evaluator picks the right type
        }
    }

    /**
     * Scheduled dispatch window — runs at configurable cron expression (default 06:00 daily).
     * This covers tenants that prefer a fixed daily planning window over event-driven triggers.
     */
    @Scheduled(cron = "${dispatch.scheduled-window-cron:0 0 6 * * *}")
    public void triggerScheduled() {
        List<Tenant> activeTenants = tenantRepository.findAllByStatus(TenantStatus.APPROVED);
        log.info("Scheduled dispatch window triggered for {} active tenants.", activeTenants.size());

        for (Tenant tenant : activeTenants) {
            evaluateTenant(tenant, PlanTriggerType.SCHEDULED);
        }
    }

    /**
     * Manual trigger — called by {@code POST /api/orders/dispatch/trigger} from Web Admin.
     * Bypasses eligibility evaluation and fires planning immediately if there are eligible orders.
     *
     * @param tenantId UUID of the tenant to trigger planning for
     */
    @Transactional
    public void triggerManual(UUID tenantId) {
        log.info("Manual planning trigger for tenant {}.", tenantId);
        boolean acquired = advisoryLockHelper.tryLock(tenantId.getMostSignificantBits());
        if (!acquired) {
            log.warn("Manual trigger for tenant {} skipped — concurrent planning already in progress.", tenantId);
            return;
        }
        try {
            orchestrationService.executePlanningCycle(tenantId, PlanTriggerType.MANUAL);
        } finally {
            advisoryLockHelper.unlock(tenantId.getMostSignificantBits());
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private void evaluateTenant(Tenant tenant, PlanTriggerType defaultTrigger) {
        UUID tenantId = tenant.getId();
        try {
            Optional<PlanTriggerType> trigger = eligibilityEvaluator.evaluate(tenant, Instant.now());
            if (trigger.isEmpty()) {
                return; // No trigger condition met
            }

            boolean acquired = advisoryLockHelper.tryLock(tenantId.getMostSignificantBits());
            if (!acquired) {
                log.debug("Tenant {} skipped — planning lock already held by another instance.", tenantId);
                return;
            }
            try {
                orchestrationService.executePlanningCycle(tenantId, trigger.get());
            } finally {
                advisoryLockHelper.unlock(tenantId.getMostSignificantBits());
            }
        } catch (Exception e) {
            // Log and continue — a failure for one tenant must not block others
            log.error("Planning evaluation failed for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }
}

