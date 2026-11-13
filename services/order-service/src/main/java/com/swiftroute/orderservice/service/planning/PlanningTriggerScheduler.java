package com.swiftroute.orderservice.service.planning;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.PlanTriggerType;
import com.swiftroute.orderservice.entity.enums.TenantStatus;
import com.swiftroute.orderservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled component that periodically evaluates whether any active tenant's
 * pending order queue should trigger a new planning cycle.
 *
 * <p>Trigger types evaluated:
 * <ul>
 *   <li>URGENT — an URGENT-priority order is waiting (cadence tick)</li>
 *   <li>BATCH_SIZE — pending orders reached the configured threshold (cadence tick)</li>
 *   <li>MAX_WAIT — oldest pending order has waited too long (cadence tick)</li>
 *   <li>SCHEDULED — tenant's configured daily dispatch window matched current hour</li>
 *   <li>MANUAL — triggered explicitly by operator via Web Admin</li>
 * </ul>
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

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TenantRepository tenantRepository;
    private final PlanningEligibilityEvaluator eligibilityEvaluator;
    private final DispatchOrchestrationService orchestrationService;
    private final AdvisoryLockHelper advisoryLockHelper;

    /**
     * Automatic evaluation cadence — runs every 2 minutes.
     * Evaluates dynamic queue conditions (URGENT, BATCH_SIZE, MAX_WAIT) for active tenants.
     */
    @Scheduled(fixedDelayString = "${dispatch.evaluation-interval-ms:120000}")
    public void evaluateAllTenants() {
        List<Tenant> activeTenants = tenantRepository.findAllByStatus(TenantStatus.APPROVED);
        log.debug("Dispatch evaluation tick: {} active tenants.", activeTenants.size());

        for (Tenant tenant : activeTenants) {
            evaluateCadenceForTenant(tenant);
        }
    }

    /**
     * Scheduled dispatch window — runs at the beginning of every hour (minute 00).
     * Matches current time (HH:mm in Vietnam zone) against each tenant's configured
     * scheduled_dispatch_time (e.g. 06:00, 07:00, 08:00).
     */
    @Scheduled(cron = "0 0 * * * *")
    public void triggerTenantScheduledWindows() {
        String currentHHmm = LocalTime.now(VIETNAM_ZONE).format(TIME_FORMATTER);
        List<Tenant> activeTenants = tenantRepository.findAllByStatus(TenantStatus.APPROVED);
        log.debug("Checking scheduled dispatch windows for {} tenants at {}.", activeTenants.size(), currentHHmm);

        for (Tenant tenant : activeTenants) {
            TenantDispatchConfig config = tenant.getDispatchConfig();
            if (config == null || !Boolean.TRUE.equals(config.getAutoDispatchEnabled())
                    || !Boolean.TRUE.equals(config.getScheduledDispatchEnabled())) {
                continue;
            }

            String targetTime = config.getScheduledDispatchTime() != null ? config.getScheduledDispatchTime() : "06:00";
            if (!targetTime.equals(currentHHmm)) {
                continue; // Not this tenant's dispatch hour
            }

            triggerScheduledForTenant(tenant.getId());
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
    // Private Helpers
    // -------------------------------------------------------------------------

    /**
     * Executes scheduled wave dispatching for a specific tenant when their time window arrives.
     * Uses SCHEDULED trigger type and acquires advisory lock to avoid multi-node duplicate runs.
     */
    private void triggerScheduledForTenant(UUID tenantId) {
        log.info("Scheduled dispatch window reached for tenant {}.", tenantId);
        boolean acquired = advisoryLockHelper.tryLock(tenantId.getMostSignificantBits());
        if (!acquired) {
            log.debug("Tenant {} scheduled window skipped — lock already held by another instance.", tenantId);
            return;
        }
        try {
            orchestrationService.executePlanningCycle(tenantId, PlanTriggerType.SCHEDULED);
        } catch (Exception e) {
            log.error("Scheduled dispatch failed for tenant {}: {}", tenantId, e.getMessage(), e);
        } finally {
            advisoryLockHelper.unlock(tenantId.getMostSignificantBits());
        }
    }

    /**
     * Evaluates dynamic queue conditions (URGENT, BATCH_SIZE, MAX_WAIT) for cadence ticks.
     */
    private void evaluateCadenceForTenant(Tenant tenant) {
        UUID tenantId = tenant.getId();
        try {
            Optional<PlanTriggerType> trigger = eligibilityEvaluator.evaluate(tenant, Instant.now());
            if (trigger.isEmpty()) {
                return; // No dynamic trigger condition met
            }

            boolean acquired = advisoryLockHelper.tryLock(tenantId.getMostSignificantBits());
            if (!acquired) {
                log.debug("Tenant {} cadence skipped — lock already held by another instance.", tenantId);
                return;
            }
            try {
                orchestrationService.executePlanningCycle(tenantId, trigger.get());
            } finally {
                advisoryLockHelper.unlock(tenantId.getMostSignificantBits());
            }
        } catch (Exception e) {
            log.error("Planning cadence evaluation failed for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }
}
