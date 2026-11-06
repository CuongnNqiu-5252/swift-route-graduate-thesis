package com.swiftroute.orderservice.service.planning;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.TenantDispatchConfig;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PlanTriggerType;
import com.swiftroute.orderservice.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Evaluates whether a tenant's pending order queue should trigger a new planning cycle,
 * and determines which trigger type applies.
 *
 * <p>This class contains only read-only evaluation logic. It never modifies database state.
 * State changes are performed by {@link DispatchOrchestrationService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanningEligibilityEvaluator {

    private final OrderRepository orderRepository;

    /**
     * Evaluates the pending order queue for a tenant and returns the first
     * matching trigger type, or empty if no trigger condition is met.
     *
     * <p>Trigger priority (highest to lowest):
     * <ol>
     *   <li>URGENT — an URGENT-priority order exists and urgent mode is IMMEDIATE_SINGLE or BATCH_IMMEDIATE</li>
     *   <li>BATCH_SIZE — pending order count has reached the configured threshold</li>
     *   <li>MAX_WAIT — the oldest pending order has waited longer than max_wait_time_seconds</li>
     * </ol>
     * SCHEDULED and MANUAL triggers are evaluated externally and not by this method.
     *
     * @param tenant     the tenant to evaluate
     * @param now        current timestamp (injected for testability)
     * @return the trigger type to use, or empty if no trigger condition is currently met
     */
    public Optional<PlanTriggerType> evaluate(Tenant tenant, Instant now) {
        TenantDispatchConfig config = tenant.getDispatchConfig();

        if (config == null || !Boolean.TRUE.equals(config.getAutoDispatchEnabled())) {
            log.debug("Tenant {} has auto_dispatch_enabled=false, skipping evaluation.", tenant.getId());
            return Optional.empty();
        }

        List<Order> pendingOrders = orderRepository.findAllByTenantIdAndStatus(
                tenant.getId(), OrderStatus.PAID);

        if (pendingOrders.isEmpty()) {
            return Optional.empty();
        }

        // --- URGENT trigger ---
        boolean hasUrgent = pendingOrders.stream()
                .anyMatch(o -> OrderPriority.URGENT == o.getPriority());
        if (hasUrgent) {
            log.info("Tenant {} URGENT trigger: found urgent-priority order in queue.", tenant.getId());
            return Optional.of(PlanTriggerType.URGENT);
        }

        // --- BATCH_SIZE trigger ---
        int threshold = config.getBatchSizeThreshold() != null ? config.getBatchSizeThreshold() : 5;
        if (pendingOrders.size() >= threshold) {
            log.info("Tenant {} BATCH_SIZE trigger: {} pending orders >= threshold {}.",
                    tenant.getId(), pendingOrders.size(), threshold);
            return Optional.of(PlanTriggerType.BATCH_SIZE);
        }

        // --- MAX_WAIT trigger ---
        int maxWaitSeconds = config.getMaxWaitTimeSeconds() != null ? config.getMaxWaitTimeSeconds() : 300;
        Optional<Instant> oldestCreatedAt = pendingOrders.stream()
                .map(Order::getCreatedAt)
                .min(Instant::compareTo);

        if (oldestCreatedAt.isPresent()) {
            long waitSeconds = now.getEpochSecond() - oldestCreatedAt.get().getEpochSecond();
            if (waitSeconds >= maxWaitSeconds) {
                log.info("Tenant {} MAX_WAIT trigger: oldest order waited {}s >= {}s threshold.",
                        tenant.getId(), waitSeconds, maxWaitSeconds);
                return Optional.of(PlanTriggerType.MAX_WAIT);
            }
        }

        log.debug("Tenant {} evaluation: {} pending orders, no trigger condition met.", tenant.getId(), pendingOrders.size());
        return Optional.empty();
    }

    /**
     * Retrieves PAID orders that are eligible for inclusion in a planning batch.
     * Filters out orders with delivery windows already expired.
     *
     * @param tenantId the tenant UUID
     * @param now      current timestamp
     * @return list of eligible orders, ordered by priority then creation time
     */
    public List<Order> getEligibleOrders(UUID tenantId, Instant now) {
        return orderRepository.findAllByTenantIdAndStatus(tenantId, OrderStatus.PAID)
                .stream()
                .filter(o -> o.getTimeWindowEnd() == null || o.getTimeWindowEnd().toInstant().isAfter(now))
                .sorted((a, b) -> {
                    // URGENT first, then HIGH, then NORMAL; within same priority sort by creation time
                    int priorityCmp = b.getPriority().ordinal() - a.getPriority().ordinal();
                    if (priorityCmp != 0) return priorityCmp;
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                })
                .toList();
    }
}

