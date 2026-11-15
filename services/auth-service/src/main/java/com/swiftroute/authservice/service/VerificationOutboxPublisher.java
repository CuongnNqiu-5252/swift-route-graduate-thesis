package com.swiftroute.authservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class VerificationOutboxPublisher {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final VerificationCipher cipher;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publish() {
        for (var row : jdbc.queryForList(
                "SELECT * FROM auth_notification_outbox WHERE published_at IS NULL AND next_attempt_at <= now() ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED")) {
            try {
                var correlation = new CorrelationData(row.get("event_id").toString());
                String eventType = row.get("event_type").toString();
                String decrypted = cipher.decrypt(row.get("encrypted_body").toString());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("eventId", row.get("event_id"));
                payload.put("eventType", eventType);
                payload.put("userId", row.get("user_id"));
                payload.put("email", row.get("email"));
                payload.put("occurredAt", Instant.now().toString());

                if ("tenant.member_permissions_updated.v1".equals(eventType)) {
                    Map<String, Object> syncData = MAPPER.readValue(decrypted, MAP_TYPE);
                    payload.putAll(syncData);
                } else {
                    String[] invitation = decrypted.split("\\n", 2);
                    payload.put("businessName", "tenant.driver_invited.v1".equals(eventType) ? invitation[0] : "");
                    payload.put("verificationUrl",
                            "tenant.driver_invited.v1".equals(eventType) && invitation.length > 1
                                    ? invitation[1]
                                    : decrypted);
                }

                rabbit.convertAndSend("swiftroute.events", eventType, payload, correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) {
                    throw new IllegalStateException("Outbox event not routed");
                }
                jdbc.update(
                        "UPDATE auth_notification_outbox SET published_at=now(),encrypted_body='' WHERE event_id=?",
                        row.get("event_id"));
            } catch (Exception ex) {
                jdbc.update(
                        "UPDATE auth_notification_outbox SET attempts=attempts+1,next_attempt_at=now()+interval '30 seconds' WHERE event_id=?",
                        row.get("event_id"));
            }
        }
    }
}
