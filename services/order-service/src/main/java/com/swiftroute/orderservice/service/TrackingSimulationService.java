package com.swiftroute.orderservice.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live order-tracking stream. The legacy class name remains for API compatibility;
 * this service no longer fabricates movement or mutates order state.
 */
@Service
public class TrackingSimulationService {
    private final Map<UUID, List<SseEmitter>> orderEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID orderId) {
        SseEmitter emitter = new SseEmitter(1_800_000L);
        List<SseEmitter> emitters = orderEmitters.computeIfAbsent(orderId, ignored -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(orderId, emitter));
        emitter.onTimeout(() -> removeEmitter(orderId, emitter));
        emitter.onError(error -> removeEmitter(orderId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .reconnectTime(3_000L)
                    .data(Map.of(
                            "order_id", orderId.toString(),
                            "status", "CONNECTED",
                            "server_time", Instant.now().toString())));
        } catch (IOException error) {
            removeEmitter(orderId, emitter);
        }
        return emitter;
    }

    public void broadcastDriverLocation(
            UUID orderId,
            UUID driverId,
            double lat,
            double lng,
            Double accuracyM,
            Double speedMps,
            Double bearing,
            Instant recordedAt,
            long sequence) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("order_id", orderId.toString());
        payload.put("driver_id", driverId.toString());
        payload.put("lat", lat);
        payload.put("lng", lng);
        payload.put("accuracy_m", accuracyM);
        payload.put("speed_mps", speedMps);
        payload.put("bearing", bearing);
        payload.put("recorded_at", recordedAt.toString());
        payload.put("sequence", sequence);
        payload.put("status", "LIVE");
        send(orderId, "location_update", payload);
    }

    public void broadcastStatus(UUID orderId, String status, String message, UUID driverId) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("order_id", orderId.toString());
        payload.put("status", status);
        payload.put("message", message);
        payload.put("timestamp", Instant.now().toString());
        if (driverId != null) payload.put("driver_id", driverId.toString());
        send(orderId, "status_update", payload);
    }

    @Scheduled(fixedDelay = 20_000L)
    public void heartbeat() {
        orderEmitters.keySet().forEach(orderId -> send(orderId, "heartbeat", Map.of(
                "order_id", orderId.toString(),
                "server_time", Instant.now().toString())));
    }

    private void send(UUID orderId, String eventName, Object payload) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        if (emitters == null) return;
        for (SseEmitter emitter : new ArrayList<>(emitters)) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception error) {
                removeEmitter(orderId, emitter);
            }
        }
    }

    private void removeEmitter(UUID orderId, SseEmitter emitter) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) orderEmitters.remove(orderId, emitters);
    }
}
