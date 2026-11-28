package com.swiftroute.notificationservice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    private final List<SseEmitter> platformEmitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(UUID tenantId) {
        List<SseEmitter> emitters = tenantEmitters.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>());
        return subscribeTo(emitters, SSE_TIMEOUT_MS, Map.of("status", "CONNECTED", "tenant_id", tenantId.toString(),
                "timestamp", Instant.now()), emitter -> removeEmitter(tenantId, emitter));
    }

    /** A distinct channel, never addressable through a tenant UUID or tenant permissions. */
    public synchronized SseEmitter subscribePlatform(long tokenLifetimeMillis) {
        if (platformEmitters.size() >= 128) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        return subscribeTo(platformEmitters, Math.max(1, Math.min(SSE_TIMEOUT_MS, tokenLifetimeMillis)),
                Map.of("status", "CONNECTED", "scope", "platform", "timestamp", Instant.now()), platformEmitters::remove);
    }

    private SseEmitter subscribeTo(List<SseEmitter> emitters, long timeout, Map<String, Object> connected,
            Consumer<SseEmitter> remove) {
        SseEmitter emitter = new SseEmitter(timeout);
        emitters.add(emitter);
        emitter.onCompletion(() -> remove.accept(emitter));
        emitter.onTimeout(() -> { remove.accept(emitter); emitter.complete(); });
        emitter.onError(e -> remove.accept(emitter));
        try {
            emitter.send(SseEmitter.event().name("connect").data(connected));
        } catch (Exception ex) {
            remove.accept(emitter);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void broadcast(UUID tenantId, String eventName, Object data) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        broadcastTo(emitters, eventName, data);
        if (emitters.isEmpty()) tenantEmitters.remove(tenantId, emitters);
    }

    public void broadcastPlatform(String eventName, Object data) {
        broadcastTo(platformEmitters, eventName, data);
    }

    private void broadcastTo(List<SseEmitter> emitters, String eventName, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception ex) {
                emitters.remove(emitter);
                emitter.completeWithError(ex);
            }
        }
    }

    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        for (UUID tenantId : tenantEmitters.keySet()) broadcast(tenantId, "ping", "heartbeat");
        broadcastPlatform("ping", "heartbeat");
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

