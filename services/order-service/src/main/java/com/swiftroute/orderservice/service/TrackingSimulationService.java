package com.swiftroute.orderservice.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.repository.OrderRepository;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service managing real-time Server-Sent Events (SSE) tracking streams for orders.
 * Supports both real driver location broadcasting and smooth autonomous route simulation
 * for demonstration purposes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingSimulationService {

    private final OrderRepository orderRepository;

    private final Map<UUID, List<SseEmitter>> orderEmitters = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> activeSimulationJobs = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private record Waypoint(double lat, double lng, double bearing, double speedKmh) {}

    /**
     * Subscribes a client to the real-time SSE stream of an order.
     * Starts automatic simulated movement along the delivery path if active.
     */
    public SseEmitter subscribe(UUID orderId) {
        // Emitter timeout: 5 minutes
        SseEmitter emitter = new SseEmitter(300_000L);

        List<SseEmitter> emitters = orderEmitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(orderId, emitter));
        emitter.onTimeout(() -> removeEmitter(orderId, emitter));
        emitter.onError(e -> removeEmitter(orderId, emitter));

        // Send initial connection acknowledgement event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "order_id", orderId.toString(),
                            "status", "CONNECTED",
                            "message", "SSE Tracking Stream Connected",
                            "timestamp", Instant.now().toString()
                    )));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE connect event for order {}: {}", orderId, e.getMessage());
            removeEmitter(orderId, emitter);
            return emitter;
        }

        // Start background autonomous simulation along the route if not already running
        startSimulationIfNeeded(orderId);

        log.info("Client subscribed to SSE tracking stream for order {}. Total listeners: {}", orderId, emitters.size());
        return emitter;
    }

    /**
     * Broadcasts a real-time GPS coordinate update to all connected SSE clients of an order.
     */
    public void broadcastLocation(UUID orderId, double lat, double lng, double bearing, double speedKmh, String status) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "order_id", orderId.toString(),
                "lat", lat,
                "lng", lng,
                "bearing", bearing,
                "speed_kmh", speedKmh,
                "status", status,
                "timestamp", Instant.now().toString()
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("location_update")
                        .data(payload));
            } catch (Exception e) {
                removeEmitter(orderId, emitter);
            }
        }
    }

    private synchronized void startSimulationIfNeeded(UUID orderId) {
        if (activeSimulationJobs.containsKey(orderId)) {
            return; // Already actively streaming for another connected client
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Cannot start SSE simulation: Order {} not found in database", orderId);
            return;
        }

        // If order is already completed or cancelled, do not simulate movement
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            broadcastStatus(orderId, order.getStatus().name(), "Đơn hàng đã kết thúc.");
            return;
        }

        double startLat = order.getPickupLat() != null ? order.getPickupLat() : 10.7769;
        double startLng = order.getPickupLng() != null ? order.getPickupLng() : 106.7009;
        double destLat = order.getDeliveryLat() != null ? order.getDeliveryLat() : 10.7770;
        double destLng = order.getDeliveryLng() != null ? order.getDeliveryLng() : 106.6953;

        final int totalSteps = 24;
        List<Waypoint> waypoints = generateWaypoints(startLat, startLng, destLat, destLng, totalSteps);

        AtomicInteger currentStep = new AtomicInteger(0);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                int step = currentStep.getAndIncrement();
                List<SseEmitter> emitters = orderEmitters.get(orderId);

                if (emitters == null || emitters.isEmpty()) {
                    // No clients listening anymore, cancel simulation
                    cancelSimulation(orderId);
                    return;
                }

                if (step < waypoints.size()) {
                    Waypoint wp = waypoints.get(step);
                    double progressPercent = Math.round((step * 100.0 / (totalSteps - 1)) * 10.0) / 10.0;

                    Map<String, Object> payload = Map.of(
                            "order_id", orderId.toString(),
                            "lat", wp.lat(),
                            "lng", wp.lng(),
                            "bearing", wp.bearing(),
                            "speed_kmh", wp.speedKmh(),
                            "step", step + 1,
                            "total_steps", totalSteps,
                            "progress_percent", progressPercent,
                            "status", "IN_TRANSIT",
                            "timestamp", Instant.now().toString()
                    );

                    for (SseEmitter emitter : emitters) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("location_update")
                                    .data(payload));
                        } catch (Exception ex) {
                            removeEmitter(orderId, emitter);
                        }
                    }
                } else {
                    // Reached destination! Send DELIVERED event
                    broadcastStatus(orderId, "DELIVERED", "Tài xế đã giao hàng thành công!");
                    cancelSimulation(orderId);
                }
            } catch (Exception e) {
                log.error("Error during SSE simulation for order {}: {}", orderId, e.getMessage());
                cancelSimulation(orderId);
            }
        }, 1000, 1800, TimeUnit.MILLISECONDS);

        activeSimulationJobs.put(orderId, future);
        log.info("Started autonomous route SSE simulation for order {} ({} steps)", orderId, totalSteps);
    }

    private void broadcastStatus(UUID orderId, String status, String message) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        if (emitters == null || emitters.isEmpty()) return;

        Map<String, Object> payload = Map.of(
                "order_id", orderId.toString(),
                "status", status,
                "message", message,
                "timestamp", Instant.now().toString()
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status_update")
                        .data(payload));
            } catch (Exception ignored) {
            }
        }
    }

    private void cancelSimulation(UUID orderId) {
        ScheduledFuture<?> future = activeSimulationJobs.remove(orderId);
        if (future != null) {
            future.cancel(false);
            log.info("Stopped SSE simulation for order {}", orderId);
        }
    }

    private void removeEmitter(UUID orderId, SseEmitter emitter) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                orderEmitters.remove(orderId);
                cancelSimulation(orderId);
            }
        }
    }

    /**
     * Generates linearly interpolated street waypoints with forward bearing calculation.
     */
    private List<Waypoint> generateWaypoints(double lat1, double lng1, double lat2, double lng2, int steps) {
        List<Waypoint> points = new ArrayList<>(steps);
        double bearing = calculateBearing(lat1, lng1, lat2, lng2);

        for (int i = 0; i < steps; i++) {
            double fraction = (double) i / (steps - 1);
            // Slight natural curve perturbation for realistic street rendering
            double curve = Math.sin(fraction * Math.PI) * 0.00035;
            double lat = lat1 + fraction * (lat2 - lat1) + curve;
            double lng = lng1 + fraction * (lng2 - lng1);
            double speed = 25.0 + (i % 4) * 2.5;

            points.add(new Waypoint(
                    Math.round(lat * 1_000_000.0) / 1_000_000.0,
                    Math.round(lng * 1_000_000.0) / 1_000_000.0,
                    Math.round(bearing * 10.0) / 10.0,
                    speed
            ));
        }
        return points;
    }

    /**
     * Calculates the great-circle initial bearing from point A to point B in degrees (0 - 360).
     */
    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        double theta = Math.atan2(y, x);

        return (Math.toDegrees(theta) + 360.0) % 360.0;
    }

    @PreDestroy
    public void cleanup() {
        activeSimulationJobs.values().forEach(f -> f.cancel(true));
        scheduler.shutdownNow();
    }
}

