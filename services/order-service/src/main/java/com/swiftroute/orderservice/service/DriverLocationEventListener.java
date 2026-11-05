package com.swiftroute.orderservice.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.config.DriverLocationEventConfig;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverLocationEventListener {
    private static final EnumSet<DeliveryTaskStatus> ACTIVE = EnumSet.of(
            DeliveryTaskStatus.ASSIGNED,
            DeliveryTaskStatus.ACCEPTED,
            DeliveryTaskStatus.ARRIVED,
            DeliveryTaskStatus.PICKED_UP,
            DeliveryTaskStatus.OUT_FOR_DELIVERY);

    private final ObjectMapper objectMapper;
    private final DeliveryTaskRepository deliveryTasks;
    private final DispatchPlanRepository dispatchPlans;
    private final TrackingSimulationService tracking;
    private final ConcurrentHashMap<UUID, Long> latestSequences = new ConcurrentHashMap<>();

    @RabbitListener(queues = DriverLocationEventConfig.QUEUE)
    public void onLocation(Message message) {
        try {
            JsonNode root = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            UUID userId = uuid(root, "user_id", "userId");
            long sequence = number(root, "sequence").longValue();
            Long currentSequence = latestSequences.get(userId);
            if (currentSequence != null && sequence <= currentSequence) {
                log.debug("Ignored out-of-order GPS event for driver {} at sequence {}", userId, sequence);
                return;
            }

            double lat = number(root, "lat").doubleValue();
            double lng = number(root, "lng").doubleValue();
            Double accuracy = nullableNumber(root, "accuracy_m", "accuracyM");
            Double speed = nullableNumber(root, "speed_mps", "speedMps");
            Double bearing = nullableNumber(root, "bearing");
            Instant recordedAt = instant(root, "recorded_at", "recordedAt");

            if (!tracking.isSimulationActive(userId)) {
                tracking.recordDriverPosition(userId, lat, lng, accuracy, speed, bearing, recordedAt, sequence);
            }

            deliveryTasks.findActiveOrderIdsByDriverId(userId, ACTIVE).forEach(orderId -> {
                if (tracking.isSimulationActive(orderId)) {
                    log.debug("Order {} is under active route simulation; skipping real GPS broadcast", orderId);
                    return;
                }
                tracking.broadcastDriverLocation(
                        orderId, userId, lat, lng, accuracy, speed, bearing, recordedAt, sequence);
            });

            dispatchPlans.findAllByDriverIdAndPlanStatusIn(userId, java.util.List.of(PlanStatus.DISPATCHED))
                    .forEach(plan -> {
                        if (tracking.isSimulationActive(plan.getId())) {
                            log.debug("Plan {} is under active route simulation; skipping real GPS broadcast", plan.getId());
                            return;
                        }
                        tracking.broadcastDriverLocation(
                                plan.getId(), userId, lat, lng, accuracy, speed, bearing, recordedAt, sequence);
                    });

            latestSequences.merge(userId, sequence, Math::max);
        } catch (Exception error) {
            log.error("Unable to process driver location event", error);
            throw new IllegalStateException("Unable to process driver location event", error);
        }
    }

    private UUID uuid(JsonNode root, String... names) {
        return UUID.fromString(text(root, names));
    }

    private String text(JsonNode root, String... names) {
        for (String name : names) if (root.hasNonNull(name)) return root.get(name).asText();
        throw new IllegalArgumentException("Missing location event field " + String.join("/", names));
    }

    private Number number(JsonNode root, String name) {
        if (!root.hasNonNull(name) || !root.get(name).isNumber()) throw new IllegalArgumentException("Missing numeric field " + name);
        return root.get(name).numberValue();
    }

    private Double nullableNumber(JsonNode root, String... names) {
        for (String name : names) if (root.hasNonNull(name) && root.get(name).isNumber()) return root.get(name).doubleValue();
        return null;
    }

    private Instant instant(JsonNode root, String... names) {
        for (String name : names) {
            if (!root.hasNonNull(name)) continue;
            JsonNode value = root.get(name);
            if (value.isTextual()) return Instant.parse(value.asText());
            if (value.isNumber()) {
                double epoch = value.doubleValue();
                if (epoch >= 1_000_000_000_000d) return Instant.ofEpochMilli(value.longValue());
                long seconds = (long) epoch;
                long nanos = Math.round((epoch - seconds) * 1_000_000_000d);
                if (nanos == 1_000_000_000L) return Instant.ofEpochSecond(seconds + 1);
                return Instant.ofEpochSecond(seconds, nanos);
            }
        }
        throw new IllegalArgumentException("Missing timestamp field " + String.join("/", names));
    }
}
