package com.swiftroute.notificationservice;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.AsyncConsumerStartedEvent;
import org.springframework.amqp.rabbit.listener.AsyncConsumerRestartedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Rabbit signal only. Audit data remains in Order Service behind its admin read APIs. */
@Component
public class PlatformAuditConsumer {
    private final NotificationSseService sse;
    private final Queue queue;
    private final ObjectMapper json = new ObjectMapper();

    public PlatformAuditConsumer(NotificationSseService sse, @Qualifier("platformAuditLiveQueue") Queue queue) {
        this.sse = sse; this.queue = queue;
    }

    @RabbitListener(queues = "#{platformAuditLiveQueue.name}")
    public void receive(Message message) throws Exception {
        try {
            if (message.getBody().length > 4096) throw new IllegalArgumentException("Oversized signal");
            var payload = json.readTree(message.getBody());
            if (payload == null || !TenantNotificationConfig.PLATFORM_AUDIT_EVENT.equals(payload.path("event_type").asText())) return;
            UUID id = UUID.fromString(payload.path("audit_id").asText());
            sse.broadcastPlatform("platform-audit", Map.of("audit_id", id.toString()));
        } catch (IllegalArgumentException | JsonProcessingException malformed) {
            throw new AmqpRejectAndDontRequeueException("Invalid platform audit signal", malformed);
        }
    }

    @EventListener({AsyncConsumerStartedEvent.class, AsyncConsumerRestartedEvent.class})
    public void recovered(ApplicationEvent event) {
        if (event.getSource() instanceof AbstractMessageListenerContainer container
                && Arrays.asList(container.getQueueNames()).contains(queue.getActualName())) {
            sse.broadcastPlatform("platform-resync", Map.of());
        }
    }
}
