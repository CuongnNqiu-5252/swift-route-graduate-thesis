package com.swiftroute.orderservice.service.tracking;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.DispatchPlanOrder;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.service.TrackingSimulationService;
import com.swiftroute.orderservice.service.TrackingSimulationService.DriverPositionSnapshot;
import com.swiftroute.orderservice.service.planning.DispatchOrchestrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Proactive delay detection service (P3.3).
 *
 * <p>Periodically compares active drivers' real-time GPS telemetry against
 * remaining stop delivery windows and planned duration. When a projected delay
 * exceeds the threshold, publishes a {@code delivery.delay_detected} event and
 * triggers safe OR-Tools reoptimization of remaining stops.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DelayDetectionService {

    private static final double URBAN_SPEED_KMH = 28.0;
    private static final double ROAD_FACTOR = 1.3;
    private static final int DELAY_THRESHOLD_MINUTES = 15;

    private final DispatchPlanRepository dispatchPlanRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TrackingSimulationService trackingSimulationService;
    private final DispatchOrchestrationService orchestrationService;

    @Scheduled(fixedDelayString = "${dispatch.delay-detection-interval-ms:120000}")
    @Transactional
    public void evaluateActiveRoutesForDelays() {
        List<DispatchPlan> activePlans = dispatchPlanRepository.findAllByPlanStatus(PlanStatus.DISPATCHED);
        if (activePlans.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (DispatchPlan plan : activePlans) {
            if (plan.getDriverId() == null) {
                continue;
            }
            Optional<DriverPositionSnapshot> posOpt = trackingSimulationService
                    .getLatestDriverPosition(plan.getDriverId());
            if (posOpt.isEmpty()) {
                continue;
            }

            DriverPositionSnapshot pos = posOpt.get();
            int maxDelayMinutes = computeMaxProjectedDelayMinutes(plan, pos.lat(), pos.lng(), now);

            if (maxDelayMinutes >= DELAY_THRESHOLD_MINUTES) {
                log.warn("Delay detected on plan {} (driver={}): projected delay {} min >= {} min threshold.",
                        plan.getId(), plan.getDriverId(), maxDelayMinutes, DELAY_THRESHOLD_MINUTES);
                publishDelayEvent(plan, maxDelayMinutes, pos.lat(), pos.lng());
                orchestrationService.reoptimizeActivePlan(
                        plan.getId(), "DELAY_DETECTED", pos.lat(), pos.lng());
            }
        }
    }

    public int computeMaxProjectedDelayMinutes(
            DispatchPlan plan,
            double currentLat,
            double currentLng,
            Instant now) {
        List<Order> remainingOrders = plan.getPlanOrders().stream()
                .sorted((a, b) -> Integer.compare(a.getStopIndex(), b.getStopIndex()))
                .map(DispatchPlanOrder::getOrder)
                .filter(o -> o.getStatus() == OrderStatus.PLANNED || o.getStatus() == OrderStatus.IN_TRANSIT)
                .toList();

        double prevLat = currentLat;
        double prevLng = currentLng;
        double cumulativeMinutes = 0.0;
        int maxDelay = 0;

        for (Order order : remainingOrders) {
            double distKm = haversineKm(prevLat, prevLng, order.getDeliveryLat(), order.getDeliveryLng()) * ROAD_FACTOR;
            double legMinutes = (distKm / URBAN_SPEED_KMH) * 60.0 + 3.0; // +3 min service time per stop
            cumulativeMinutes += legMinutes;

            if (order.getTimeWindowEnd() != null) {
                Instant projectedArrival = now.plusSeconds((long) (cumulativeMinutes * 60));
                long delaySec = projectedArrival.getEpochSecond() - order.getTimeWindowEnd().toInstant().getEpochSecond();
                if (delaySec > 0) {
                    int delayMin = (int) Math.ceil(delaySec / 60.0);
                    if (delayMin > maxDelay) {
                        maxDelay = delayMin;
                    }
                }
            }

            prevLat = order.getDeliveryLat();
            prevLng = order.getDeliveryLng();
        }
        return maxDelay;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private void publishDelayEvent(DispatchPlan plan, int delayMinutes, double lat, double lng) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plan_id", plan.getId().toString());
        payload.put("tenant_id", plan.getTenant().getId().toString());
        payload.put("driver_id", plan.getDriverId().toString());
        payload.put("estimated_delay_minutes", delayMinutes);
        payload.put("driver_lat", lat);
        payload.put("driver_lng", lng);

        outboxEventRepository.save(OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("DispatchPlan")
                .aggregateId(plan.getId())
                .eventType("delivery.delay_detected")
                .payload(payload)
                .build());
    }
}

