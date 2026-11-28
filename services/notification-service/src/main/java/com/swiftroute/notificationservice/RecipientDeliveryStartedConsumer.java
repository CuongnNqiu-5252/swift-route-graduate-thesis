package com.swiftroute.notificationservice;

import java.io.IOException;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "recipient-notifications.delivery-started.enabled", havingValue = "true")
class RecipientDeliveryStartedConsumer {
    private static final DateTimeFormatter WINDOW_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM", Locale.of("vi", "VN")).withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JdbcTemplate jdbc;
    private final RecipientDeliveryDetailsClient details;
    private final RecipientDeliverySender sender;
    private final RecipientDeliveryOutcomes outcomes;
    private final URI trackingOrigin;
    private final ObjectMapper json = new ObjectMapper();

    RecipientDeliveryStartedConsumer(JdbcTemplate jdbc, RecipientDeliveryDetailsClient details,
            RecipientDeliverySender sender, RecipientDeliveryOutcomes outcomes,
            @Value("${WEB_APP_URL:http://localhost:3000}") String webAppUrl) {
        this.jdbc = jdbc;
        this.details = details;
        this.sender = sender;
        this.outcomes = outcomes;
        trackingOrigin = URI.create(webAppUrl);
        if (trackingOrigin.getHost() == null || trackingOrigin.getUserInfo() != null || trackingOrigin.getQuery() != null
                || trackingOrigin.getFragment() != null
                || !("https".equalsIgnoreCase(trackingOrigin.getScheme()) || "http".equalsIgnoreCase(trackingOrigin.getScheme()))) {
            throw new IllegalArgumentException("WEB_APP_URL must be a valid HTTP(S) base URL");
        }
    }

    @RabbitListener(queues = RecipientDeliveryQueueConfig.QUEUE, containerFactory = "recipientDeliveryListenerFactory")
    @Transactional
    public void receive(Message message) {
        Event event = readEvent(message);
        int inserted = jdbc.update("""
                INSERT INTO processed_events(event_id, consumer_name)
                VALUES (?, 'recipient-delivery-started') ON CONFLICT DO NOTHING
                """, event.eventId());
        if (inserted == 0) {
            return;
        }

        var current = details.findCurrent(event.tenantId(), event.eventId())
                .filter(delivery -> isPresent(delivery.recipientPhone()) && isPresent(delivery.trackingToken()));
        if (current.isEmpty()) {
            outcomes.record(event.eventId(), RecipientDeliveryOutcomes.State.SKIPPED);
            return;
        }
        var delivery = current.get();
        sender.send(event.eventId(), delivery.recipientPhone().trim(), compose(delivery));
        outcomes.record(event.eventId(), RecipientDeliveryOutcomes.State.SIMULATED);
    }

    private Event readEvent(Message message) {
        try {
            var payload = json.readTree(message.getBody());
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("Event payload must be an object");
            }
            UUID eventId = UUID.fromString(payload.path("event_id").asText());
            UUID tenantId = UUID.fromString(payload.path("tenant_id").asText());
            String messageId = message.getMessageProperties().getMessageId();
            if (messageId != null && !eventId.toString().equals(messageId)) {
                throw new IllegalArgumentException("Message ID mismatch");
            }
            return new Event(eventId, tenantId);
        } catch (IOException | IllegalArgumentException ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid recipient delivery-started event");
        }
    }

    private String compose(RecipientDeliveryDetailsClient.Details delivery) {
        String carrier = isPresent(delivery.carrierName()) ? delivery.carrierName().strip().replaceAll("\\s+", " ") : "SwiftRoute Express";
        String orderCode = delivery.orderCode().strip().replaceAll("\\s+", " ");
        String type = delivery.eventType() == null ? RecipientDeliveryQueueConfig.DELIVERY_STARTED : delivery.eventType();
        String status = switch (type) {
            case RecipientDeliveryQueueConfig.DELIVERY_COMPLETED -> " đã được giao.";
            case RecipientDeliveryQueueConfig.DELIVERY_FAILED -> " chưa giao được.";
            default -> " đang được giao.";
        };
        String text = "Đơn " + orderCode + " của " + carrier + status;
        if (RecipientDeliveryQueueConfig.DELIVERY_STARTED.equals(type)
                && delivery.timeWindowStart() != null && delivery.timeWindowEnd() != null) {
            text += " Khung giờ dự kiến " + WINDOW_FORMAT.format(delivery.timeWindowStart()) + " – "
                    + WINDOW_FORMAT.format(delivery.timeWindowEnd()) + ".";
        }
        String link = UriComponentsBuilder.fromUri(trackingOrigin).pathSegment("track", delivery.trackingToken())
                .build().encode().toUriString();
        return text + " Theo dõi đơn tại " + link;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private record Event(UUID eventId, UUID tenantId) {}
}
