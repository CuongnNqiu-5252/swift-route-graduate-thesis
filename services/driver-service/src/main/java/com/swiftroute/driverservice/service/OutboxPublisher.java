package com.swiftroute.driverservice.service;

import java.time.LocalDateTime;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.driverservice.config.DriverEventConfig;
import com.swiftroute.driverservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes committed outbox records to RabbitMQ.
 * Failed records are retried automatically on subsequent scheduler runs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().forEach(event -> {
            try {
                rabbitTemplate.convertAndSend(
                        DriverEventConfig.EVENT_EXCHANGE,
                        event.getEventType(),
                        event.getPayload());
                event.setPublishedAt(LocalDateTime.now());
            } catch (RuntimeException ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                log.warn("Unable to publish driver event id={}, type={}: {}",
                        event.getEventId(), event.getEventType(), ex.getMessage());
            }
        });
    }
}
