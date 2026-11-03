package com.swiftroute.notificationservice;

import java.util.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantNotificationConsumer {
    private final JdbcTemplate jdbc;
    private final VerificationCipher cipher;
    private final ObjectMapper json=new ObjectMapper();
    @RabbitListener(queues=TenantNotificationConfig.QUEUE)
    @Transactional
    public void receive(Message message) throws Exception {
        var p=json.readTree(message.getBody());
        String type=p.path("eventType").asText(p.path("event_type").asText());
        if (!Set.of("user.email_verification.v1","tenant.created.v1","tenant.verification_submitted.v1","tenant.verification_changes_requested.v1","tenant.approved.v1","tenant.rejected.v1","tenant.suspended.v1").contains(type))return;
        UUID eventId=UUID.fromString(p.path("eventId").asText(p.path("event_id").asText()));
        if(jdbc.update("INSERT INTO processed_events(event_id,consumer_name) VALUES (?,'tenant-notifications') ON CONFLICT DO NOTHING",eventId)==0)return;
        String user=p.path("userId").asText(p.path("ownerUserId").asText());
        String email=p.path("email").asText();
        if(email.isBlank()) throw new IllegalArgumentException("Notification email missing");
        UUID tenant=p.hasNonNull("tenantId")?UUID.fromString(p.get("tenantId").asText()):null;
        String title=type.equals("user.email_verification.v1")?"Verify your SwiftRoute email":"SwiftRoute organization update: "+type;
        String body=type.equals("user.email_verification.v1")?"Verify your email (valid for 24 hours): "+p.path("verificationUrl").asText()
            :"Organization: "+tenant+"\nStatus: "+type+"\nFeedback: "+p.path("reason").asText()+"\nOpen your SwiftRoute workspace to continue.";
        jdbc.update("INSERT INTO notification_deliveries(delivery_id,tenant_id,user_id,channel,template_code,title,body,recipient_email,encrypted_body,status) VALUES (?,?,?,'EMAIL',?,?,?, ?,?,'PENDING')",eventId,tenant,UUID.fromString(user),type,title,"Email content encrypted at rest",email,cipher.encrypt(body));
    }
}
