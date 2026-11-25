package com.swiftroute.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sun.net.httpserver.HttpServer;

class RecipientDeliveryStartedConsumerTest {
    private final UUID eventId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RecipientDeliveryDetailsClient details = mock(RecipientDeliveryDetailsClient.class);
    private final RecipientDeliverySender sender = mock(RecipientDeliverySender.class);
    private final RecipientDeliveryOutcomes outcomes = mock(RecipientDeliveryOutcomes.class);
    private final RecipientDeliveryStartedConsumer consumer =
            new RecipientDeliveryStartedConsumer(jdbc, details, sender, outcomes, "https://swift.example");

    @Test
    void sendsOneMessageWithVietnamTimeAndTrackingLinkOnReplay() {
        when(jdbc.update(anyString(), eq(eventId))).thenReturn(1, 0);
        when(details.findCurrent(tenantId, eventId)).thenReturn(Optional.of(delivery(true)));

        consumer.receive(message());
        consumer.receive(message());

        verify(sender).send(eventId, "0901234567",
                "Đơn SR-1042 của Kho Cần Thơ đang được giao. Khung giờ dự kiến 14:00 24/11 – 16:00 24/11."
                        + " Theo dõi đơn tại https://swift.example/track/TOKEN123");
        verify(details).findCurrent(tenantId, eventId);
    }

    @Test
    void skipsEventsThatAreNoLongerCurrent() {
        when(jdbc.update(anyString(), eq(eventId))).thenReturn(1);
        when(details.findCurrent(tenantId, eventId)).thenReturn(Optional.empty());

        consumer.receive(message());

        verifyNoInteractions(sender);
    }

    @Test
    void rejectsAnEventWithAMismatchedMessageIdBeforeReadingAnyOrderData() {
        Message malformed = MessageBuilder.fromClonedMessage(message())
                .setMessageId(UUID.randomUUID().toString()).build();

        assertThatThrownBy(() -> consumer.receive(malformed)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(jdbc, details, sender);
    }

    @Test
    void listenerAdviceRetriesATransientSendingFailure() {
        when(jdbc.update(anyString(), eq(eventId))).thenReturn(1);
        when(details.findCurrent(tenantId, eventId)).thenReturn(Optional.of(delivery(false)));
        String text = "Đơn SR-1042 của Kho Cần Thơ đang được giao."
                + " Theo dõi đơn tại https://swift.example/track/TOKEN123";
        doThrow(new IllegalStateException("Simulated temporary failure"))
                .doNothing().when(sender).send(eventId, "0901234567", text);

        var factory = new RecipientDeliveryQueueConfig().recipientDeliveryListenerFactory(
                mock(SimpleRabbitListenerContainerFactoryConfigurer.class), mock(ConnectionFactory.class), outcomes);
        ProxyFactory proxy = new ProxyFactory(consumer);
        proxy.addAdvice(factory.getAdviceChain()[0]);
        ((RecipientDeliveryStartedConsumer) proxy.getProxy()).receive(message());

        verify(sender, times(2)).send(eventId, "0901234567", text);
    }

    @Test
    void readsRecipientDataFromTheAuthenticatedInternalEndpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/tenants/" + tenantId + "/recipient-delivery-starts/" + eventId, exchange -> {
            try (exchange) {
                if (!"test-service-key".equals(exchange.getRequestHeaders().getFirst("X-Service-Key"))) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                byte[] response = """
                        {
                          "recipient_phone": "0901234567",
                          "tracking_token": "TOKEN123",
                          "order_code": "SR-1042",
                          "carrier_name": "Kho Cần Thơ",
                          "time_window_start": "2026-11-24T07:00:00Z",
                          "time_window_end": "2026-11-24T09:00:00Z"
                        }
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            var client = new RecipientDeliveryDetailsClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-service-key");
            assertThat(client.findCurrent(tenantId, eventId)).contains(delivery(true));
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "order.recipient_delivery_failed.v1,chưa giao được",
        "order.recipient_delivery_completed.v1,đã được giao"
    })
    void sendsNeutralResultMessagesAndRecordsSimulatedOutcome(String eventType, String description) {
        when(jdbc.update(anyString(), eq(eventId))).thenReturn(1);
        when(details.findCurrent(tenantId, eventId)).thenReturn(Optional.of(
                new RecipientDeliveryDetailsClient.Details("0901234567", "TOKEN123", "SR-1042", "Kho Cần Thơ",
                        null, null, eventType)));

        consumer.receive(message());

        verify(sender).send(eventId, "0901234567",
                "Đơn SR-1042 của Kho Cần Thơ " + description + ". Theo dõi đơn tại https://swift.example/track/TOKEN123");
        verify(outcomes).record(eventId, RecipientDeliveryOutcomes.State.SIMULATED);
    }

    private Message message() {
        String payload = """
                {"event_id":"%s","tenant_id":"%s"}
                """.formatted(eventId, tenantId);
        return MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setMessageId(eventId.toString()).build();
    }

    private RecipientDeliveryDetailsClient.Details delivery(boolean hasWindow) {
        return new RecipientDeliveryDetailsClient.Details("0901234567", "TOKEN123", "SR-1042", "Kho Cần Thơ",
                hasWindow ? OffsetDateTime.parse("2026-11-24T07:00:00Z") : null,
                hasWindow ? OffsetDateTime.parse("2026-11-24T09:00:00Z") : null, null);
    }
}
