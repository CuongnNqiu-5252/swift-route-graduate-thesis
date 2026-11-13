package com.swiftroute.notificationservice;

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

import lombok.extern.slf4j.Slf4j;

/**
 * Manages Server-Sent Events (SSE) connections for tenant operators.
 * Pushes live dispatch alerts, incidents, and resolutions directly to Web Admin without polling.
 */
@Service
@Slf4j
public class NotificationSseService {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes
    private final Map<UUID, List<SseEmitter>> tenantEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tenantId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        List<SseEmitter> emitters = tenantEmitters.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(tenantId, emitter));
        emitter.onTimeout(() -> removeEmitter(tenantId, emitter));
        emitter.onError(e -> removeEmitter(tenantId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "tenant_id", tenantId.toString(),
                            "timestamp", Instant.now().toString()
                    )));
        } catch (IOException ex) {
            removeEmitter(tenantId, emitter);
        }

        log.info("SSE client connected for tenant {} (total listeners: {})", tenantId, emitters.size());
        return emitter;
    }

    public void broadcast(UUID tenantId, String eventName, Object data) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception ex) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            if (emitters.isEmpty()) {
                tenantEmitters.remove(tenantId);
            }
        }
    }

    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        for (Map.Entry<UUID, List<SseEmitter>> entry : tenantEmitters.entrySet()) {
            UUID tenantId = entry.getKey();
            List<SseEmitter> emitters = entry.getValue();
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("heartbeat"));
                } catch (Exception ex) {
                    dead.add(emitter);
                }
            }
            if (!dead.isEmpty()) {
                emitters.removeAll(dead);
                if (emitters.isEmpty()) {
                    tenantEmitters.remove(tenantId);
                }
            }
        }
    }

    private void removeEmitter(UUID tenantId, SseEmitter emitter) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                tenantEmitters.remove(tenantId);
            }
        }
        log.debug("SSE client disconnected for tenant {}", tenantId);
    }
}

