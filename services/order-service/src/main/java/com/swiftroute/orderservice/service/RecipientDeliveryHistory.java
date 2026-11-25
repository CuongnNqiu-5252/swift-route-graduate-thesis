package com.swiftroute.orderservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecipientDeliveryHistory {
    private final JdbcTemplate jdbc;
    private final RestClient notifications;

    public RecipientDeliveryHistory(JdbcTemplate jdbc,
            @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8086}") String url,
            @Value("${tenant.internal-key:}") String serviceKey) {
        this.jdbc = jdbc;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        notifications = RestClient.builder().baseUrl(url).defaultHeader("X-Service-Key", serviceKey)
                .requestFactory(factory).build();
    }

    public List<Entry> list(UUID tenantId, UUID orderId) {
        List<Pending> events = jdbc.query("""
                SELECT e.event_id, e.event_type, CAST(e.payload->>'attempt_number' AS integer) AS attempt_number,
                       COALESCE(e.payload->>'occurred_at', e.payload->>'started_at') AS occurred_at,
                       e.dlq_published_at IS NOT NULL AS dead_lettered
                FROM outbox_events e
                JOIN orders o ON e.payload->>'order_id' = CAST(o.order_id AS text)
                WHERE o.order_id = ? AND o.tenant_id = ? AND e.payload->>'tenant_id' = CAST(o.tenant_id AS text)
                  AND e.event_type IN ('order.delivery_started.v1', 'order.recipient_delivery_failed.v1',
                                       'order.recipient_delivery_completed.v1')
                ORDER BY e.created_at DESC, e.event_id DESC
                LIMIT 100
                """, (rs, row) -> new Pending(rs.getObject("event_id", UUID.class), rs.getString("event_type"),
                rs.getObject("attempt_number", Integer.class), Instant.parse(rs.getString("occurred_at")),
                rs.getBoolean("dead_lettered")), orderId, tenantId);
        if (events.isEmpty()) {
            return List.of();
        }
        Map<String, String> outcomes;
        try {
            outcomes = notifications.post().uri("/internal/recipient-delivery-outcomes")
                    .body(events.stream().map(Pending::eventId).toList())
                    .retrieve().body(new ParameterizedTypeReference<Map<String, String>>() {});
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (outcomes == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        return events.stream().map(event -> new Entry(event.eventId(), switch (event.eventType()) {
            case RecipientDeliveryEvents.FAILED -> "FAILED";
            case RecipientDeliveryEvents.COMPLETED -> "COMPLETED";
            default -> "STARTED";
        }, event.attemptNumber(), event.occurredAt(),
                outcomes.getOrDefault(event.eventId().toString(), event.deadLettered() ? "FAILED" : "PENDING")))
                .toList();
    }

    private record Pending(UUID eventId, String eventType, Integer attemptNumber, Instant occurredAt,
                           boolean deadLettered) {}
    public record Entry(UUID eventId, String milestone, Integer attemptNumber, Instant occurredAt, String status) {}
}
