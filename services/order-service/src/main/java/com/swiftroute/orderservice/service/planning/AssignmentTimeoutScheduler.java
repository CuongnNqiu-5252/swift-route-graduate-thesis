package com.swiftroute.orderservice.service.planning;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically checks for DISPATCHED plans whose driver acknowledgement
 * deadline has expired without an acknowledgement or decline, and triggers
 * automated reassignment or escalation (P2.1 & P2.2).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentTimeoutScheduler {

    private final DispatchPlanRepository dispatchPlanRepository;
    private final DispatchOrchestrationService orchestrationService;

    @Scheduled(fixedDelayString = "${dispatch.ack-timeout-check-ms:60000}")
    public void checkTimedOutAssignments() {
        Instant now = Instant.now();
        List<DispatchPlan> timedOutPlans = dispatchPlanRepository.findTimedOutDispatchedPlans(now);
        if (timedOutPlans.isEmpty()) {
            return;
        }
        log.info("Found {} timed-out dispatch plan(s) awaiting driver acknowledgement.", timedOutPlans.size());
        for (DispatchPlan plan : timedOutPlans) {
            try {
                orchestrationService.handleAcknowledgementTimeout(plan.getId());
            } catch (Exception ex) {
                log.error("Failed to process acknowledgement timeout for plan {}: {}",
                        plan.getId(), ex.getMessage(), ex);
            }
        }
    }
}

