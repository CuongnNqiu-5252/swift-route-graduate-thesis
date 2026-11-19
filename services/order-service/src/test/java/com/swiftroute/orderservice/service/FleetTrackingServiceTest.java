package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;

class FleetTrackingServiceTest {

    @Test
    @DisplayName("DriverLocationEventListener forwards tenant_id updates to fleet SSE stream")
    void forwardsTenantLocationToFleetBroadcast() {
        DeliveryTaskRepository deliveryTasks = mock(DeliveryTaskRepository.class);
        DispatchPlanRepository dispatchPlans = mock(DispatchPlanRepository.class);
        TrackingSimulationService tracking = mock(TrackingSimulationService.class);
        DriverLocationEventListener listener = new DriverLocationEventListener(
                new ObjectMapper(), deliveryTasks, dispatchPlans, tracking);

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(tracking.isSimulationActive(userId)).thenReturn(false);
        when(deliveryTasks.findActiveOrderIdsByDriverId(eq(userId), any())).thenReturn(List.of());
        when(dispatchPlans.findAllByDriverIdAndPlanStatusIn(eq(userId), any())).thenReturn(List.of());

        String json = """
                {
                  "user_id": "%s",
                  "tenant_id": "%s",
                  "lat": 10.7769,
                  "lng": 106.7009,
                  "recorded_at": "2026-11-17T10:00:00Z",
                  "sequence": 100
                }
                """.formatted(userId, tenantId);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        listener.onLocation(message);

        verify(tracking).broadcastFleetDriverLocation(
                eq(tenantId), eq(userId), eq(10.7769), eq(106.7009),
                any(), any(), any(), eq(Instant.parse("2026-11-17T10:00:00Z")), eq(100L));
    }

    @Test
    @DisplayName("DriverLocationEventListener skips fleet broadcast when tenant_id is absent")
    void skipsFleetBroadcastWithoutTenantId() {
        DeliveryTaskRepository deliveryTasks = mock(DeliveryTaskRepository.class);
        DispatchPlanRepository dispatchPlans = mock(DispatchPlanRepository.class);
        TrackingSimulationService tracking = mock(TrackingSimulationService.class);
        DriverLocationEventListener listener = new DriverLocationEventListener(
                new ObjectMapper(), deliveryTasks, dispatchPlans, tracking);

        UUID userId = UUID.randomUUID();
        when(tracking.isSimulationActive(userId)).thenReturn(false);
        when(deliveryTasks.findActiveOrderIdsByDriverId(eq(userId), any())).thenReturn(List.of());
        when(dispatchPlans.findAllByDriverIdAndPlanStatusIn(eq(userId), any())).thenReturn(List.of());

        String json = """
                {
                  "user_id": "%s",
                  "lat": 10.7769,
                  "lng": 106.7009,
                  "recorded_at": "2026-11-17T10:00:00Z",
                  "sequence": 101
                }
                """.formatted(userId);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        listener.onLocation(message);

        verify(tracking, never()).broadcastFleetDriverLocation(
                any(), any(), eq(10.7769), eq(106.7009), any(), any(), any(), any(), eq(101L));
    }

    @Test
    @DisplayName("TrackingSimulationService subscribeFleet returns active emitter after fleet broadcast")
    void subscribeFleetStoresRecentSnapshot() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TrackingSimulationService service = new TrackingSimulationService(redis, new ObjectMapper());
        UUID tenantId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        service.broadcastFleetDriverLocation(
                tenantId, driverId, 10.78, 106.71, 5.0, 6.0, 90.0, Instant.now(), 1L);

        SseEmitter emitter = service.subscribeFleet(tenantId);
        assertThat(emitter).isNotNull();
        service.shutdownScheduler();
    }

    @Test
    @DisplayName("TrackingSimulationService subscribeFleet restores saved driver positions from Redis cache")
    @SuppressWarnings("unchecked")
    void subscribeFleetRestoresSavedRedisPositions() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        ZSetOperations<String, String> zsetOps = mock(ZSetOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForZSet()).thenReturn(zsetOps);

        UUID tenantId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        when(hashOps.entries("tracking:fleet:" + tenantId)).thenReturn(Map.of());
        when(zsetOps.range("driver:locations:" + tenantId, 0, -1)).thenReturn(Set.of(driverId.toString()));
        when(hashOps.entries("driver:location:" + driverId)).thenReturn(Map.of(
                "lat", "10.79",
                "lng", "106.72",
                "recorded_at", Instant.now().toString(),
                "sequence", "55"));

        TrackingSimulationService service = new TrackingSimulationService(redis, new ObjectMapper());
        SseEmitter emitter = service.subscribeFleet(tenantId);
        assertThat(emitter).isNotNull();
        verify(hashOps).entries("driver:location:" + driverId);
        service.shutdownScheduler();
    }
}
