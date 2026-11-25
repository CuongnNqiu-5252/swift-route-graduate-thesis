package com.swiftroute.orderservice.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class RecipientDeliveryEvents {
    static final String STARTED = "order.delivery_started.v1";
    static final String FAILED = "order.recipient_delivery_failed.v1";
    static final String COMPLETED = "order.recipient_delivery_completed.v1";
    static final Set<String> ROUTING_KEYS = Set.of(STARTED, FAILED, COMPLETED);

    private final OutboxEventRepository outbox;
    @Value("${recipient-notifications.delivery-started.enabled:false}")
    private boolean enabled;

    void recordResult(DeliveryAttempt attempt) {
        if (!enabled) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        outbox.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("DELIVERY_TASK")
                .aggregateId(attempt.getTask().getId())
                .eventType("SUCCESS".equals(attempt.getOutcome()) ? COMPLETED : FAILED)
                .payload(Map.of(
                        "event_id", eventId.toString(),
                        "tenant_id", attempt.getTenantId().toString(),
                        "order_id", attempt.getOrder().getId().toString(),
                        "attempt_number", attempt.getAttemptNumber(),
                        "occurred_at", attempt.getCompletedAt().toString()))
                .build());
    }
}
