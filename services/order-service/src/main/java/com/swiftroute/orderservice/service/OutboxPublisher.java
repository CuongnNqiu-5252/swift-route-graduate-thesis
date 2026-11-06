package com.swiftroute.orderservice.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.config.TenantEventConfig;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes committed outbox records with publisher confirms, exponential backoff (1s, 2s, 4s, 8s, 16s)
 * and Dead-Letter Queue (DLQ) routing when retries exceed the threshold.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    public static final int MAX_RETRIES = 5;
    public static final long CONFIRM_TIMEOUT_SECONDS = 5L;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock = Clock.systemUTC();

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        LocalDateTime now = LocalDateTime.now(clock);
        outboxEventRepository.findPendingEventsForPublish().forEach(event -> {
            try {
                publishWithConfirm(TenantEventConfig.EVENT_EXCHANGE, event.getEventType(), event.getPayload(), event.getEventId());
                event.setPublishedAt(now);
                event.setLastError(null);
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                int newRetry = event.getRetryCount() + 1;
                event.setRetryCount(newRetry);
                event.setFailedAt(now);
                event.setLastError(ex.getMessage());

                if (newRetry >= MAX_RETRIES) {
                    log.error("Outbox event {} exceeded max retries ({}). Attempting Dead Letter Queue {} routing.",
                            event.getEventId(), MAX_RETRIES, TenantEventConfig.DLQ_QUEUE, ex);
                    try {
                        publishWithConfirm(TenantEventConfig.DLX_EXCHANGE, "dlq." + event.getEventType(), event.getPayload(), event.getEventId());
                        event.setDlqPublishedAt(now);
                        event.setLastError("Exceeded max retries (" + MAX_RETRIES + "); routed to DLQ");
                        log.info("Outbox event {} confirmed into DLQ.", event.getEventId());
                    } catch (Exception dlqEx) {
                        log.error("Failed to route outbox event {} to DLQ: {}. Will retry DLQ publish.", event.getEventId(), dlqEx.getMessage());
                        event.setLastError("DLQ publish failed: " + dlqEx.getMessage());
                        event.setNextAttemptAt(now.plusSeconds(16L)); // Remains recoverable!
                    }
                } else {
                    long backoffSeconds = Math.min(16L, 1L << (newRetry - 1));
                    event.setNextAttemptAt(now.plusSeconds(backoffSeconds));
                    log.warn("Unable to publish event {} (retry {}/{}, next in {}s): {}",
                            event.getEventId(), newRetry, MAX_RETRIES, backoffSeconds, ex.getMessage());
                }
                outboxEventRepository.save(event);
            }
        });
    }

    private void publishWithConfirm(String exchange, String routingKey, Object payload, UUID eventId) throws Exception {
        CorrelationData correlationData = new CorrelationData(eventId.toString());
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlationData);
        if (correlationData.getFuture() != null) {
            try {
                CorrelationData.Confirm confirm = correlationData.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (confirm != null && !confirm.isAck()) {
                    throw new AmqpException("Broker returned NACK for event " + eventId + ": " + confirm.getReason());
                }
            } catch (TimeoutException te) {
                throw new AmqpException("Timed out waiting for broker confirm for event " + eventId, te);
            }
        }
    }
}
