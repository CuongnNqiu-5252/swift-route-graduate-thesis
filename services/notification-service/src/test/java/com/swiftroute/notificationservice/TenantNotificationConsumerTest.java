package com.swiftroute.notificationservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantNotificationConsumerTest {

    private JdbcTemplate jdbc;
    private NotificationSseService sseService;
    private EmailNotificationSender emailSender;
    private TenantNotificationConsumer consumer;

    private final UUID eventId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        sseService = mock(NotificationSseService.class);
        emailSender = mock(EmailNotificationSender.class);
        consumer = new TenantNotificationConsumer(jdbc, sseService, emailSender);

        when(jdbc.update(contains("processed_events"), any(Object.class))).thenReturn(1);
    }

    private Message createMessage(String eventType, String jsonPayload) {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey(eventType);
        return new Message(jsonPayload.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    @DisplayName("Email verification event sends email but does NOT broadcast via SSE")
    void emailVerification_SendsEmail_DoesNotBroadcastSSE() throws Exception {
        String payload = """
                {
                    "eventId": "%s",
                    "eventType": "user.email_verification.v1",
                    "userId": "%s",
                    "tenantId": "%s",
                    "email": "user@example.com",
                    "verificationUrl": "https://swiftroute.com/verify?token=SECRET_TOKEN_123"
                }
                """.formatted(eventId, userId, tenantId);

        consumer.receive(createMessage("user.email_verification.v1", payload));

        verify(emailSender).send(eq("user@example.com"), anyString(), contains("SECRET_TOKEN_123"));
        verify(sseService, never()).broadcast(any(), any(), any());
    }

    @Test
    @DisplayName("Password reset event sends email but does NOT broadcast via SSE")
    void passwordReset_SendsEmail_DoesNotBroadcastSSE() throws Exception {
        String payload = """
                {
                    "eventId": "%s",
                    "eventType": "user.password_reset_requested.v1",
                    "userId": "%s",
                    "tenantId": "%s",
                    "email": "reset@example.com",
                    "verificationUrl": "https://swiftroute.com/reset?token=SECRET_RESET_TOKEN"
                }
                """.formatted(eventId, userId, tenantId);

        consumer.receive(createMessage("user.password_reset_requested.v1", payload));

        verify(emailSender).send(eq("reset@example.com"), anyString(), contains("SECRET_RESET_TOKEN"));
        verify(sseService, never()).broadcast(any(), any(), any());
    }

    @Test
    @DisplayName("Driver invite event sends email but does NOT broadcast via SSE")
    void driverInvite_SendsEmail_DoesNotBroadcastSSE() throws Exception {
        String payload = """
                {
                    "eventId": "%s",
                    "eventType": "tenant.driver_invited.v1",
                    "userId": "%s",
                    "tenantId": "%s",
                    "email": "driver@example.com",
                    "businessName": "Fast Logistics",
                    "verificationUrl": "https://swiftroute.com/invite?token=INVITE_TOKEN_XYZ"
                }
                """.formatted(eventId, userId, tenantId);

        consumer.receive(createMessage("tenant.driver_invited.v1", payload));

        verify(emailSender).send(eq("driver@example.com"), anyString(), contains("INVITE_TOKEN_XYZ"));
        verify(sseService, never()).broadcast(any(), any(), any());
    }

    @Test
    @DisplayName("Tenant approved event broadcasts via SSE")
    void tenantApproved_BroadcastsSSE() throws Exception {
        String payload = """
                {
                    "eventId": "%s",
                    "eventType": "tenant.approved.v1",
                    "userId": "%s",
                    "tenantId": "%s"
                }
                """.formatted(eventId, userId, tenantId);

        consumer.receive(createMessage("tenant.approved.v1", payload));

        verify(sseService).broadcast(eq(tenantId), eq("operational-alert"), any());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void scheduledImportResultAppearsInInboxAndStreamsAfterCommitOnlyOnce() throws Exception {
        String type = TenantNotificationConsumer.IMPORT_COMPLETED;
        String body = "Đã tạo 7 vận đơn. Cần bổ sung địa chỉ: 2. Trùng mã: 3. Dòng lỗi: 1.";
        Message result = createMessage(type, """
                {"eventId":"%s","tenantId":"%s","eventType":"%s",
                 "created":7,"needsAddress":2,"duplicates":3,"invalid":1}
                """.formatted(eventId, tenantId, type));
        when(jdbc.update(contains("processed_events"), eq(eventId))).thenReturn(1, 0);

        TransactionSynchronizationManager.initSynchronization();
        try {
            consumer.receive(result);
            consumer.receive(result);
            verify(jdbc).update(contains("INSERT INTO notification_deliveries"),
                    eq(eventId), eq(tenantId), eq(tenantId), eq(type),
                    eq("Đã chạy nhập vận đơn tự động"), eq(body));
            verify(sseService, never()).broadcast(any(), any(), any());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(sseService).broadcast(eq(tenantId), eq("operational-alert"), org.mockito.ArgumentMatchers.argThat(
                data -> data instanceof Map<?, ?> map && body.equals(map.get("body"))
                    && "/operation-flow".equals(map.get("action_url"))));
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }
}
