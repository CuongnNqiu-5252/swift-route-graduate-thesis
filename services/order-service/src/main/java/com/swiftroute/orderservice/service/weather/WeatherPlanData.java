package com.swiftroute.orderservice.service.weather;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeatherPlanData {
    private final DispatchPlanRepository plans;
    private final ObjectMapper json;

    public record Stop(UUID planId, UUID orderId, String orderCode, Double lat, Double lng,
            Instant eta, Instant deadline) {}

    @Transactional(readOnly = true)
    public List<Stop> load(UUID tenantId, UUID planId) {
        return load(tenantId, planId, null);
    }

    @Transactional(readOnly = true)
    public List<Stop> load(UUID tenantId, UUID planId, Instant referenceNow) {
        var selected = planId == null
                ? plans.findAllByTenantIdAndPlanStatusIn(tenantId,
                        List.of(PlanStatus.DRAFT, PlanStatus.APPROVED, PlanStatus.DISPATCHED))
                : plans.findByIdAndTenantId(planId, tenantId).stream().toList();
        List<Stop> result = new ArrayList<>();
        for (var plan : selected) {
            if (!List.of(PlanStatus.DRAFT, PlanStatus.APPROVED, PlanStatus.DISPATCHED).contains(plan.getPlanStatus())) continue;
            Instant departure = resolveDeparture(plan, referenceNow);
            Map<UUID, Instant> arrivals = new HashMap<>();
            try {
                double previous = 0;
                var points = json.readTree(plan.getRouteWaypoints() == null ? "[]" : plan.getRouteWaypoints());
                if (points.isArray() && departure != null) for (var point : points) {
                    if (!point.path("estimated_arrival_minutes").isNumber()) continue;
                    double minutes = point.path("estimated_arrival_minutes").asDouble();
                    if (!Double.isFinite(minutes) || minutes < previous || minutes > 7 * 1440) continue;
                    try {
                        UUID id = UUID.fromString(point.path("stop_id").asText());
                        arrivals.put(id, departure.plusMillis(Math.round(minutes * 60_000)));
                        previous = minutes;
                    } catch (IllegalArgumentException ignored) { }
                }
            } catch (Exception ignored) {
                // An unreadable snapshot is unmeasured, never a zero-minute ETA.
            }
            for (var order : plan.getOrdersInStopOrder()) {
                if (!List.of(OrderStatus.READY_FOR_DISPATCH, OrderStatus.PLANNED, OrderStatus.IN_TRANSIT).contains(order.getStatus())) continue;
                String code = order.getExternalOrderCode();
                result.add(new Stop(plan.getId(), order.getId(), code == null || code.isBlank()
                        ? "#" + order.getId().toString().substring(0, 8) : code.substring(0, Math.min(code.length(), 80)),
                        order.getDeliveryLat(), order.getDeliveryLng(), arrivals.get(order.getId()),
                        order.getTimeWindowStart() != null && order.getTimeWindowEnd() != null
                                && order.getTimeWindowEnd().isAfter(order.getTimeWindowStart())
                                ? order.getTimeWindowEnd().toInstant() : null));
            }
        }
        return List.copyOf(result);
    }

    private static Instant resolveDeparture(com.swiftroute.orderservice.entity.DispatchPlan plan, Instant referenceNow) {
        if (plan.getDispatchedAt() != null) {
            return plan.getDispatchedAt();
        }
        Instant created = plan.getCreatedAt();
        if (created == null) {
            return referenceNow;
        }
        return referenceNow != null && created.isBefore(referenceNow) ? referenceNow : created;
    }
}
