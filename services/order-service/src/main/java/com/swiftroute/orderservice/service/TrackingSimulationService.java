package com.swiftroute.orderservice.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.dto.request.StartSimulationRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Live order and dispatch-plan tracking stream via Server-Sent Events (SSE),
 * Redis snapshot persistence, and on-demand street-waypoint GPS simulation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingSimulationService {

    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(10);
    private static final String DRIVER_KEY_PREFIX = "tracking:driver:";
    private static final String STREAM_KEY_PREFIX = "tracking:stream:";
    private static final String SIM_WAYPOINTS_KEY_PREFIX = "tracking:sim:waypoints:";
    private static final String SIM_ACTIVE_KEY_PREFIX = "tracking:sim:active:";

    public record DriverPositionSnapshot(
            UUID driverId,
            double lat,
            double lng,
            Double accuracyM,
            Double speedMps,
            Double bearing,
            Instant recordedAt,
            long sequence
    ) {}

    private final Map<UUID, List<SseEmitter>> orderEmitters = new ConcurrentHashMap<>();
    private final Map<UUID, DriverPositionSnapshot> latestDriverPositions = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> activeSimulations = new ConcurrentHashMap<>();
    private final Set<UUID> simulatingStreams = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService simulationScheduler = Executors.newScheduledThreadPool(2);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @PreDestroy
    public void shutdownScheduler() {
        simulationScheduler.shutdownNow();
    }

    public SseEmitter subscribe(UUID streamKey) {
        SseEmitter emitter = new SseEmitter(1_800_000L);
        List<SseEmitter> emitters = orderEmitters.computeIfAbsent(streamKey, ignored -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(streamKey, emitter));
        emitter.onTimeout(() -> removeEmitter(streamKey, emitter));
        emitter.onError(error -> removeEmitter(streamKey, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .reconnectTime(3_000L)
                    .data(Map.of(
                            "order_id", streamKey.toString(),
                            "status", "CONNECTED",
                            "simulating", isSimulationActive(streamKey),
                            "server_time", Instant.now().toString())));
            readSnapshot(STREAM_KEY_PREFIX + streamKey).ifPresent(snapshot -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("location_snapshot")
                            .data(locationPayload(streamKey, snapshot, "SNAPSHOT", null, null)));
                } catch (IOException error) {
                    removeEmitter(streamKey, emitter);
                }
            });
        } catch (IOException error) {
            removeEmitter(streamKey, emitter);
        }
        return emitter;
    }

    /**
     * Checks whether an order or dispatch plan stream is currently under active simulation lock.
     * When true, real GPS events from mobile devices are suppressed for this stream to prevent flickering.
     */
    public boolean isSimulationActive(UUID streamKey) {
        if (streamKey == null) return false;
        if (simulatingStreams.contains(streamKey)) return true;
        try {
            return Boolean.TRUE.equals(redis.hasKey(SIM_ACTIVE_KEY_PREFIX + streamKey));
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * Starts an on-demand route simulation that stores the route waypoints in Redis
     * and streams each coordinate sequentially over SSE to subscribed clients.
     */
    public void startRouteSimulation(
            UUID primaryStreamKey,
            UUID driverId,
            List<UUID> companionStreamKeys,
            List<StartSimulationRequest.Waypoint> waypoints,
            Integer requestedIntervalMs) {
        if (waypoints == null || waypoints.isEmpty()) {
            return;
        }

        UUID effectiveDriverId = driverId != null ? driverId : primaryStreamKey;
        List<UUID> allStreams = new ArrayList<>();
        allStreams.add(primaryStreamKey);
        if (companionStreamKeys != null) {
            for (UUID key : companionStreamKeys) {
                if (key != null && !allStreams.contains(key)) {
                    allStreams.add(key);
                }
            }
        }

        stopRouteSimulation(primaryStreamKey, companionStreamKeys);

        int intervalMs = requestedIntervalMs != null ? Math.clamp(requestedIntervalMs, 250, 5_000) : 1_000;
        int totalSteps = waypoints.size();

        persistSimulationWaypoints(primaryStreamKey, waypoints);
        for (UUID streamKey : allStreams) {
            setSimulationLock(streamKey, true);
        }

        AtomicInteger indexRef = new AtomicInteger(0);
        ScheduledFuture<?> future = simulationScheduler.scheduleAtFixedRate(() -> {
            try {
                int idx = indexRef.getAndIncrement();
                if (idx >= totalSteps) {
                    stopRouteSimulation(primaryStreamKey, companionStreamKeys);
                    return;
                }

                StartSimulationRequest.Waypoint current = waypoints.get(idx);
                StartSimulationRequest.Waypoint prev = idx > 0 ? waypoints.get(idx - 1) : current;
                double bearing = idx > 0
                        ? calculateBearing(prev.lat(), prev.lng(), current.lat(), current.lng())
                        : 0.0;
                Instant now = Instant.now();
                long sequence = now.toEpochMilli();

                recordDriverPosition(
                        effectiveDriverId,
                        current.lat(),
                        current.lng(),
                        5.0,
                        8.33,
                        bearing,
                        now,
                        sequence);

                for (UUID streamKey : allStreams) {
                    broadcastDriverLocationWithStep(
                            streamKey,
                            effectiveDriverId,
                            current.lat(),
                            current.lng(),
                            5.0,
                            8.33,
                            bearing,
                            now,
                            sequence,
                            "SIMULATING",
                            idx + 1,
                            totalSteps);
                }

                if (idx == totalSteps - 1) {
                    stopRouteSimulation(primaryStreamKey, companionStreamKeys);
                }
            } catch (Exception ex) {
                log.warn("Error in route simulation step for {}: {}", primaryStreamKey, ex.getMessage());
            }
        }, 0L, intervalMs, TimeUnit.MILLISECONDS);

        activeSimulations.put(primaryStreamKey, future);
        log.info("Started GPS route simulation for stream {} ({} waypoints, interval={}ms, companions={})",
                primaryStreamKey, totalSteps, intervalMs, allStreams.size() - 1);
    }

    /**
     * Stops an active route simulation and releases the simulation lock on all associated streams.
     */
    public void stopRouteSimulation(UUID primaryStreamKey, List<UUID> companionStreamKeys) {
        ScheduledFuture<?> existing = activeSimulations.remove(primaryStreamKey);
        if (existing != null) {
            existing.cancel(false);
        }
        setSimulationLock(primaryStreamKey, false);
        if (companionStreamKeys != null) {
            for (UUID key : companionStreamKeys) {
                if (key != null) {
                    ScheduledFuture<?> compFuture = activeSimulations.remove(key);
                    if (compFuture != null) {
                        compFuture.cancel(false);
                    }
                    setSimulationLock(key, false);
                }
            }
        }
    }

    public void recordDriverPosition(
            UUID driverId,
            double lat,
            double lng,
            Double accuracyM,
            Double speedMps,
            Double bearing,
            Instant recordedAt,
            long sequence) {
        latestDriverPositions.put(driverId, new DriverPositionSnapshot(
                driverId, lat, lng, accuracyM, speedMps, bearing, recordedAt, sequence));
        writeSnapshot(DRIVER_KEY_PREFIX + driverId, latestDriverPositions.get(driverId));
    }

    public Optional<DriverPositionSnapshot> getLatestDriverPosition(UUID driverId) {
        DriverPositionSnapshot local = latestDriverPositions.get(driverId);
        return local != null ? Optional.of(local) : readSnapshot(DRIVER_KEY_PREFIX + driverId);
    }

    public void broadcastDriverLocation(
            UUID streamKey,
            UUID driverId,
            double lat,
            double lng,
            Double accuracyM,
            Double speedMps,
            Double bearing,
            Instant recordedAt,
            long sequence) {
        broadcastDriverLocationWithStep(
                streamKey, driverId, lat, lng, accuracyM, speedMps, bearing, recordedAt, sequence, "LIVE", null, null);
    }

    private void broadcastDriverLocationWithStep(
            UUID streamKey,
            UUID driverId,
            double lat,
            double lng,
            Double accuracyM,
            Double speedMps,
            Double bearing,
            Instant recordedAt,
            long sequence,
            String status,
            Integer step,
            Integer totalSteps) {
        DriverPositionSnapshot snapshot = new DriverPositionSnapshot(
                driverId, lat, lng, accuracyM, speedMps, bearing, recordedAt, sequence);
        writeSnapshot(STREAM_KEY_PREFIX + streamKey, snapshot);
        Map<String, Object> payload = locationPayload(streamKey, snapshot, status, step, totalSteps);
        send(streamKey, "location_update", payload);
    }

    public void broadcastStatus(UUID orderId, String status, String message, UUID driverId) {
        Map<String, Object> payload = new LinkedHashMap<>();
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

    private Map<String, Object> locationPayload(
            UUID streamKey,
            DriverPositionSnapshot snapshot,
            String status,
            Integer step,
            Integer totalSteps) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stream_id", streamKey.toString());
        payload.put("driver_id", snapshot.driverId().toString());
        payload.put("lat", snapshot.lat());
        payload.put("lng", snapshot.lng());
        payload.put("accuracy_m", snapshot.accuracyM());
        payload.put("speed_mps", snapshot.speedMps());
        payload.put("speed_kmh", snapshot.speedMps() != null ? snapshot.speedMps() * 3.6 : 30.0);
        payload.put("bearing", snapshot.bearing());
        payload.put("recorded_at", snapshot.recordedAt().toString());
        payload.put("sequence", snapshot.sequence());
        payload.put("status", status);
        if (step != null) payload.put("step", step);
        if (totalSteps != null) payload.put("total_steps", totalSteps);
        return payload;
    }

    private void persistSimulationWaypoints(UUID streamKey, List<StartSimulationRequest.Waypoint> waypoints) {
        try {
            redis.opsForValue().set(
                    SIM_WAYPOINTS_KEY_PREFIX + streamKey,
                    objectMapper.writeValueAsString(waypoints),
                    SNAPSHOT_TTL);
        } catch (JsonProcessingException | RuntimeException error) {
            log.warn("Unable to persist simulation waypoints for {}: {}", streamKey, error.getMessage());
        }
    }

    private void setSimulationLock(UUID streamKey, boolean active) {
        if (streamKey == null) return;
        if (active) {
            simulatingStreams.add(streamKey);
            try {
                redis.opsForValue().set(SIM_ACTIVE_KEY_PREFIX + streamKey, "true", SNAPSHOT_TTL);
            } catch (RuntimeException error) {
                log.debug("Redis unavailable for simulation lock {}: {}", streamKey, error.getMessage());
            }
        } else {
            simulatingStreams.remove(streamKey);
            try {
                redis.delete(SIM_ACTIVE_KEY_PREFIX + streamKey);
            } catch (RuntimeException error) {
                log.debug("Redis unavailable when clearing simulation lock {}: {}", streamKey, error.getMessage());
            }
        }
    }

    private static double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double y = Math.sin(dLng) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private void writeSnapshot(String key, DriverPositionSnapshot snapshot) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(snapshot), SNAPSHOT_TTL);
        } catch (JsonProcessingException | RuntimeException error) {
            log.warn("Unable to persist tracking snapshot {}: {}", key, error.getMessage());
        }
    }

    private Optional<DriverPositionSnapshot> readSnapshot(String key) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null || value.isBlank()) return Optional.empty();
            return Optional.of(objectMapper.readValue(value, DriverPositionSnapshot.class));
        } catch (Exception error) {
            log.warn("Unable to restore tracking snapshot {}: {}", key, error.getMessage());
            return Optional.empty();
        }
    }
}
