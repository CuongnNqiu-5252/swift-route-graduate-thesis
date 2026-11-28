package com.swiftroute.notificationservice;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;

@Component
@ConditionalOnProperty(name = "recipient-notifications.delivery-started.enabled", havingValue = "true")
class RecipientDeliveryDetailsClient {
    private final RestClient orders;

    RecipientDeliveryDetailsClient(@Value("${ORDER_SERVICE_URL:http://localhost:8082}") String orderUrl,
            @Value("${TENANT_INTERNAL_KEY:}") String serviceKey) {
        if (serviceKey.isBlank()) {
            throw new IllegalStateException("Set TENANT_INTERNAL_KEY before enabling recipient delivery notifications");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        orders = RestClient.builder().baseUrl(orderUrl).defaultHeader("X-Service-Key", serviceKey)
                .requestFactory(factory).build();
    }

    Optional<Details> findCurrent(UUID tenantId, UUID eventId) {
        try {
            var response = orders.get()
                    .uri("/internal/tenants/{tenantId}/recipient-delivery-starts/{eventId}", tenantId, eventId)
                    .retrieve().toEntity(Details.class);
            if (response.getStatusCode().value() == 204) {
                return Optional.empty();
            }
            if (response.getBody() == null) {
                throw new IllegalStateException("Order Service returned no recipient delivery details");
            }
            return Optional.of(response.getBody());
        } catch (RestClientException ex) {
            throw new IllegalStateException("Unable to load recipient delivery details from Order Service");
        }
    }

    record Details(
            @JsonProperty("recipient_phone") String recipientPhone,
            @JsonProperty("tracking_token") String trackingToken,
            @JsonProperty("order_code") String orderCode,
            @JsonProperty("carrier_name") String carrierName,
            @JsonProperty("time_window_start") OffsetDateTime timeWindowStart,
            @JsonProperty("time_window_end") OffsetDateTime timeWindowEnd,
            @JsonProperty("event_type") String eventType) {}
}
