package com.swiftroute.orderservice.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.config.TenantEventConfig;
import com.swiftroute.orderservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Publishes committed outbox records; failed records remain retryable in PostgreSQL. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock = Clock.systemUTC();

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().forEach(event -> {
            try {
                rabbitTemplate.convertAndSend(TenantEventConfig.EVENT_EXCHANGE, event.getEventType(), event.getPayload());
                event.setPublishedAt(LocalDateTime.now(clock));
            } catch (RuntimeException ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                log.warn("Unable to publish tenant event {}", event.getEventId(), ex);
            }
        });
    }
}
