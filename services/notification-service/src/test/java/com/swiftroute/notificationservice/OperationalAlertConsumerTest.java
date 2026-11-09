package com.swiftroute.notificationservice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationalAlertConsumerTest {
    private JdbcTemplate jdbc;
    private OperationalAlertConsumer consumer;
    private final UUID eventId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        consumer = new OperationalAlertConsumer(jdbc);
    }

    private Message event(String type) {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey(type);
        properties.setMessageId(eventId.toString());
        String payload = "{\"tenant_id\":\"%s\",\"driver_id\":\"%s\",\"incident_id\":\"%s\"}"
                .formatted(tenantId, driverId, UUID.randomUUID());
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void incidentIsAddressedToTenantOperatorsUsingStableMessageIdentity() {
        when(jdbc.update(contains("processed_events"), eq(eventId))).thenReturn(1);
        consumer.onOperationalEvent(event("delivery.incident"));
        verify(jdbc).update(contains("notification_deliveries"), eq(eventId), eq(tenantId), eq(tenantId),
                eq("IN_APP"), eq("delivery.incident"), anyString(), anyString(), anyString());
    }

    @Test
    void duplicateEventDoesNotInsertAnotherNotification() {
        consumer.onOperationalEvent(event("delivery.incident"));
        verify(jdbc).update(contains("processed_events"), eq(eventId));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void resolutionIsPersistedAsIndependentEvent() {
        when(jdbc.update(contains("processed_events"), eq(eventId))).thenReturn(1);
        consumer.onOperationalEvent(event("incident.resolved"));
        verify(jdbc).update(contains("notification_deliveries"), eq(eventId), eq(tenantId), eq(tenantId),
                eq("IN_APP"), eq("incident.resolved"), anyString(), anyString(), anyString());
    }

    @Test
    void completedDeliveryIsAddressedToTenantInbox() {
        when(jdbc.update(contains("processed_events"), eq(eventId))).thenReturn(1);
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey("order.delivery_completed.v1");
        properties.setMessageId(eventId.toString());
        String payload = """
                {"eventId":"%s","orderId":"%s","tenantId":"%s","assignedDriverId":"%s","orderCode":"ORD-8899","recipientName":"Nguyen Van B","codAmount":150000}
                """.formatted(eventId, UUID.randomUUID(), tenantId, driverId);
        consumer.onOperationalEvent(new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
        verify(jdbc).update(contains("notification_deliveries"), eq(eventId), eq(tenantId), eq(tenantId),
                eq("IN_APP"), eq("order.delivery_completed.v1"), contains("ORD-8899"), contains("Nguyen Van B"), anyString());
    }

    @Test
    void dispatchedPlanIsAddressedToDriverAsPushNotification() {
        when(jdbc.update(contains("processed_events"), eq(eventId))).thenReturn(1);
        consumer.onOperationalEvent(event("plan.dispatched"));
        verify(jdbc).update(contains("notification_deliveries"), eq(eventId), eq(tenantId), eq(driverId),
                eq("PUSH"), eq("plan.dispatched"), anyString(), anyString(), anyString());
    }

    @Test
    void databaseFailureEscapesListenerSoTransactionRollsBack() {
        when(jdbc.update(contains("processed_events"), eq(eventId))).thenReturn(1);
        when(jdbc.update(contains("notification_deliveries"), eq(eventId), eq(tenantId), eq(tenantId),
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        assertThatThrownBy(() -> consumer.onOperationalEvent(event("delivery.incident")))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }
}
