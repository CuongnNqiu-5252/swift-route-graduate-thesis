package com.swiftroute.notificationservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryJob {
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final VerificationCipher cipher;
    @Value("${notification.from:no-reply@swiftroute.local}") private String from;
    @Scheduled(fixedDelay=5000)
    @Transactional
    public void deliver() {
        for(var row:jdbc.queryForList("SELECT * FROM notification_deliveries WHERE channel='EMAIL' AND status IN ('PENDING','FAILED') AND recipient_email IS NOT NULL AND next_attempt_at <= now() ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED")) {
            try {
                var msg=new SimpleMailMessage();msg.setFrom(from);msg.setTo(row.get("recipient_email").toString());
                msg.setSubject(row.get("title").toString());msg.setText(cipher.decrypt(row.get("encrypted_body").toString()));mail.send(msg);
                jdbc.update("UPDATE notification_deliveries SET status='SENT',sent_at=now(),encrypted_body=NULL,provider_response=NULL WHERE delivery_id=?",row.get("delivery_id"));
            } catch(Exception ex) {
                // Retain encrypted content and retry without logging tokens, SMTP credentials or message bodies.
                jdbc.update("UPDATE notification_deliveries SET status='FAILED',attempts=attempts+1,next_attempt_at=now()+interval '60 seconds',provider_response='SMTP delivery failed' WHERE delivery_id=?",row.get("delivery_id"));
            }
        }
    }
}
