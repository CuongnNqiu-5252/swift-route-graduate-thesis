package com.swiftroute.orderservice.service;

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
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.swiftroute.orderservice.config.TenantEventConfig;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.repository.OutboxEventRepository;

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
                .aggregateType("order")
                .aggregateId(UUID.randomUUID())
                .eventType("order.created")
                .payload("{\"test\": true}")
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .retryCount(0)
                .build();

        when(outboxEventRepository.findPendingEventsForPublish()).thenReturn(List.of(event));

        // Simulate broker ACK confirmation
        doAnswer(inv -> {
            MessagePostProcessor processor = inv.getArgument(3);
            Message message = processor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
            assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo(eventId.toString());
            CorrelationData cd = inv.getArgument(4);
            if (cd != null) {
                cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));

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
                .aggregateType("order")
                .aggregateId(UUID.randomUUID())
                .eventType("order.created")
                .payload("{\"test\": true}")
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .retryCount(0)
                .build();

        when(outboxEventRepository.findPendingEventsForPublish()).thenReturn(List.of(event));

        // Simulate broker NACK confirmation
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(4);
            if (cd != null) {
                cd.getFuture().complete(new CorrelationData.Confirm(false, "Queue quota exceeded"));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));

        outboxPublisher.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getFailedAt()).isNotNull();
        assertThat(saved.getNextAttemptAt()).isNotNull();
        assertThat(saved.getLastError()).contains("Queue quota exceeded");
    }

    @Test
    @DisplayName("Exceeding max retries routes event to DLQ and awaits confirmation before setting dlq_published_at")
    void exceedingMaxRetriesRoutesToDlqWithConfirmation() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("order")
                .aggregateId(UUID.randomUUID())
                .eventType("order.created")
                .payload("{\"test\": true}")
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .retryCount(4) // 4 prior retries, this 5th attempt will exceed MAX_RETRIES (5)
                .build();

        when(outboxEventRepository.findPendingEventsForPublish()).thenReturn(List.of(event));

        // Normal publish throws broker connection exception
        doAnswer(inv -> {
            String exchange = inv.getArgument(0);
            CorrelationData cd = inv.getArgument(4);
            if (TenantEventConfig.EVENT_EXCHANGE.equals(exchange)) {
                throw new AmqpException("Broker unreachable");
            } else if (TenantEventConfig.DLX_EXCHANGE.equals(exchange)) {
                // DLQ publish succeeds and is confirmed
                cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));

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
                .aggregateType("order")
                .aggregateId(UUID.randomUUID())
                .eventType("order.created")
                .payload("{\"test\": true}")
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .retryCount(4)
                .build();

        when(outboxEventRepository.findPendingEventsForPublish()).thenReturn(List.of(event));

        // Both regular and DLQ publishing fail
        doThrow(new AmqpException("Broker connection failure"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));

        outboxPublisher.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getDlqPublishedAt()).isNull(); // NEVER marked complete!
        assertThat(saved.getNextAttemptAt()).isNotNull(); // Scheduled for retry
        assertThat(saved.getLastError()).contains("DLQ publish failed");
    }
}
