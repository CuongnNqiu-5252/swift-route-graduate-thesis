package com.swiftroute.authservice.service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class VerificationOutboxPublisher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final VerificationCipher cipher;
    @Scheduled(fixedDelay=5000)
    @Transactional
    public void publish() {
        for (var row: jdbc.queryForList("SELECT * FROM auth_notification_outbox WHERE published_at IS NULL AND next_attempt_at <= now() ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED")) {
            try {
                var correlation=new CorrelationData(row.get("event_id").toString());
                String eventType = row.get("event_type").toString();
                String decrypted = cipher.decrypt(row.get("encrypted_body").toString());
                String[] invitation = decrypted.split("\\n", 2);
                rabbit.convertAndSend("swiftroute.events",eventType,Map.of(
                    "eventId",row.get("event_id"),"eventType",eventType,"userId",row.get("user_id"),
                    "email",row.get("email"),
                    "businessName", eventType.equals("tenant.driver_invited.v1") ? invitation[0] : "",
                    "verificationUrl", eventType.equals("tenant.driver_invited.v1") && invitation.length > 1 ? invitation[1] : decrypted,
                    "occurredAt",java.time.Instant.now().toString()),correlation);
                var confirm=correlation.getFuture().get(5,TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned()!=null) throw new IllegalStateException("Email event not routed");
                jdbc.update("UPDATE auth_notification_outbox SET published_at=now(),encrypted_body='' WHERE event_id=?",row.get("event_id"));
            } catch (Exception ex) {
                jdbc.update("UPDATE auth_notification_outbox SET attempts=attempts+1,next_attempt_at=now()+interval '30 seconds' WHERE event_id=?",row.get("event_id"));
            }
        }
    }
}
