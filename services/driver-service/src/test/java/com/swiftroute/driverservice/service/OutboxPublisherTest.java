package com.swiftroute.driverservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.swiftroute.driverservice.config.DriverEventConfig;
import com.swiftroute.driverservice.entity.OutboxEvent;
import com.swiftroute.driverservice.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventRepository, rabbitTemplate);
    }

    @Test
    @DisplayName("Publisher confirm success sets published_at and clears last_error")
    void publisherConfirmSuccessSetsPublishedAt() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("driver")
                .aggregateId(UUID.randomUUID())
                .eventType("driver.registered")
                .payload("{\"test\": true}")
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .retryCount(0)
                .build();

        when(outboxEventRepository.findPendingEventsForPublish()).thenReturn(List.of(event));

        // Simulate broker ACK confirmation
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            if (cd != null) {
                cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        outboxPublisher.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getPublishedAt()).isNotNull();
        assertThat(saved.getDlqPublishedAt()).isNull();
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    @DisplayName("Broker NACK increments retry count, computes exponential backoff, and leaves published_at null")
    void brokerNackIncrementsRetryAndAppliesExponentialBackoff() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("driver")
                .aggregateId(UUID.randomUUID())
                .eventType("driver.registered")
                .payload("{\"test\": true}")
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .retryCount(0)
                .build();

        when(outboxEventRepository.findPendingEventsForPublish()).thenReturn(List.of(event));

        // Simulate broker NACK confirmation
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            if (cd != null) {
                cd.getFuture().complete(new CorrelationData.Confirm(false, "Connection refused"));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        outboxPublisher.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getFailedAt()).isNotNull();
        assertThat(saved.getNextAttemptAt()).isNotNull();
        assertThat(saved.getLastError()).contains("Connection refused");
    }

    @Test
    @DisplayName("Exceeding max retries routes event to DLQ and awaits confirmation before setting dlq_published_at")
    void exceedingMaxRetriesRoutesToDlqWithConfirmation() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("driver")
                .aggregateId(UUID.randomUUID())
                .eventType("driver.registered")
                .payload("{\"test\": true}")
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .retryCount(4)
                .build();

        when(outboxEventRepository.findPendingEventsForPublish()).thenReturn(List.of(event));

        doAnswer(inv -> {
            String exchange = inv.getArgument(0);
            CorrelationData cd = inv.getArgument(3);
            if (DriverEventConfig.EVENT_EXCHANGE.equals(exchange)) {
                throw new AmqpException("Broker unreachable");
            } else if (DriverEventConfig.DLX_EXCHANGE.equals(exchange)) {
                cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        outboxPublisher.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getRetryCount()).isEqualTo(5);
        assertThat(saved.getDlqPublishedAt()).isNotNull();
        assertThat(saved.getLastError()).contains("routed to DLQ");
    }

    @Test
    @DisplayName("Failed DLQ publish does not mark event completed and schedules next attempt")
    void failedDlqPublishRemainsRecoverable() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("driver")
                .aggregateId(UUID.randomUUID())
                .eventType("driver.registered")
                .payload("{\"test\": true}")
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .retryCount(4)
                .build();

        when(outboxEventRepository.findPendingEventsForPublish()).thenReturn(List.of(event));

        doThrow(new AmqpException("Broker connection failure"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        outboxPublisher.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getDlqPublishedAt()).isNull();
        assertThat(saved.getNextAttemptAt()).isNotNull();
        assertThat(saved.getLastError()).contains("DLQ publish failed");
    }
}

