package com.swiftroute.orderservice.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.config.OrderDispatchEventConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for order dispatch events published by the Routing Engine.
 * Supports both on-demand single order auto-dispatch and batch plan dispatch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderDispatchEventListener {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    @RabbitListener(queues = OrderDispatchEventConfig.QUEUE)
    public void onOrderDispatched(Message message) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            log.info("Received order dispatch event: {}", json);
            JsonNode root = objectMapper.readTree(json);
            UUID eventId = root.has("eventId") ? UUID.fromString(root.get("eventId").asText()) : null;

            if (root.has("assignments") && root.get("assignments").isArray()) {
                // Batch assignment event
                UUID planId = root.has("planId") ? UUID.fromString(root.get("planId").asText()) : null;
                for (JsonNode item : root.get("assignments")) {
                    if (item.has("orderId") && item.has("driverId")) {
                        UUID orderId = UUID.fromString(item.get("orderId").asText());
                        UUID driverId = UUID.fromString(item.get("driverId").asText());
                        orderService.assignDriverFromDispatch(orderId, driverId, planId, eventId);
                    }
                }
            } else if (root.has("orderId") && root.has("driverId")) {
                // Single on-demand auto-dispatch event
                UUID orderId = UUID.fromString(root.get("orderId").asText());
                UUID driverId = UUID.fromString(root.get("driverId").asText());
                orderService.assignDriverFromDispatch(orderId, driverId, null, eventId);
            } else {
                log.warn("Unrecognized dispatch event payload structure: {}", json);
            }
        } catch (Exception e) {
            log.error("Failed to process order dispatch message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed processing order dispatch event", e);
        }
    }
}

