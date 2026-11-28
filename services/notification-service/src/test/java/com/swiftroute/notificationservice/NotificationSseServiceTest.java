package com.swiftroute.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseServiceTest {

    private NotificationSseService sseService;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sseService = new NotificationSseService();
    }

    @Test
    void subscribeReturnsEmitterAndAllowsBroadcast() {
        SseEmitter emitter = sseService.subscribe(tenantId);
        assertThat(emitter).isNotNull();

        // Broadcast to existing tenant
        sseService.broadcast(tenantId, "dispatch-alert", Map.of("title", "Test alert"));

        // Heartbeat does not throw
        sseService.sendHeartbeat();
    }

    @Test
    void broadcastToNonExistentTenantIsNoOp() {
        sseService.broadcast(UUID.randomUUID(), "dispatch-alert", Map.of("title", "No listener"));
    }
}

