package com.swiftroute.notificationservice;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.AsyncConsumerStartedEvent;

class PlatformAuditConsumerTest {
    private Message message(String body) { return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties()); }

    @Test
    void forwardsOnlyAuditIdToThePlatformChannel() throws Exception {
        var sse = mock(NotificationSseService.class);
        var consumer = new PlatformAuditConsumer(sse, new AnonymousQueue());
        UUID id = UUID.randomUUID();
        consumer.receive(message("{\"event_type\":\"platform.audit-recorded.v1\",\"audit_id\":\"" + id + "\",\"secret\":\"not-forwarded\"}"));
        verify(sse).broadcastPlatform("platform-audit", Map.of("audit_id", id.toString()));
        verifyNoMoreInteractions(sse);
    }

    @Test
    void unrelatedSignalsAreIgnoredAndMalformedSignalsDoNotRequeueForever() throws Exception {
        var sse = mock(NotificationSseService.class);
        var consumer = new PlatformAuditConsumer(sse, new AnonymousQueue());
        consumer.receive(message("{\"event_type\":\"tenant.created.v1\"}"));
        assertThatThrownBy(() -> consumer.receive(message("{\"event_type\":\"platform.audit-recorded.v1\",\"audit_id\":\"invalid\"}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(sse);
    }

    @Test
    void consumerRecoveryResynchronizesOnlyItsOwnPlatformQueue() {
        var sse = mock(NotificationSseService.class); var queue = new AnonymousQueue();
        var consumer = new PlatformAuditConsumer(sse, queue);
        var container = mock(AbstractMessageListenerContainer.class);
        when(container.getQueueNames()).thenReturn(new String[] { "some-tenant-queue" }, new String[] { queue.getActualName() });
        consumer.recovered(new AsyncConsumerStartedEvent(container, new Object()));
        verifyNoInteractions(sse);
        consumer.recovered(new AsyncConsumerStartedEvent(container, new Object()));
        verify(sse).broadcastPlatform("platform-resync", Map.of());
    }
}
