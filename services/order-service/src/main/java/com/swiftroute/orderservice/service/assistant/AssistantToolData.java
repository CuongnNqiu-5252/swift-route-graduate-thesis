package com.swiftroute.orderservice.service.assistant;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssistantToolData {
    private static final int EXAMPLE_LIMIT = 8;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<PlanStatus> OPEN_STAGES = List.of(PlanStatus.DRAFT, PlanStatus.APPROVED, PlanStatus.DISPATCHED);

    private final OrderService orderService;
    private final DispatchPlanRepository planRepository;
    private final ObjectMapper json;

    public record Reference(UUID planId, String label) {}
    public record Result(Map<String, Object> facts, List<Reference> routes) {}
    private record DepotBacklog(String depotName, int waiting, long oldestMinutes) {}
    private record Stop(UUID planId, Instant eta) {}
    private record Appointment(String orderCode, UUID planId, String deadline, String eta, Long bufferMinutes) {}

    @Transactional(readOnly = true)
    public Result waitingOrders(AssistantScope scope) {
        var queue = orderService.getOperationsQueue(scope.tenantId(), scope.userId(), scope.role());
        Instant now = Instant.now();
        var groups = queue.readyForDispatch().stream().collect(Collectors.groupingBy(
                order -> Objects.toString(order.pickupLocationId(), "unknown")));
        var depots = groups.values().stream()
                .map(group -> new DepotBacklog(depotLabel(group.getFirst().pickupLocationName()),
                        group.size(), group.stream().map(OrderResponse::createdAt)
                                .filter(Objects::nonNull).mapToLong(created -> minutesSince(created, now)).max().orElse(0)))
                .sorted(Comparator.comparingLong(DepotBacklog::oldestMinutes).reversed())
                .limit(EXAMPLE_LIMIT)
                .toList();
        return new Result(Map.of("asOfVietnam", local(now), "totalWaiting", queue.readyForDispatch().size(),
                "totalDepots", groups.size(), "depotExamplesOldestFirst", depots), List.of());
    }

    @Transactional(readOnly = true)
    public Result appointmentRisks(AssistantScope scope) {
        var queue = orderService.getOperationsQueue(scope.tenantId(), scope.userId(), scope.role());
        Instant now = Instant.now();
        Map<UUID, Stop> stops = new HashMap<>();
        openPlans(scope).forEach(plan -> readStops(plan, stops));
        var appointments = Stream.concat(queue.readyForDispatch().stream(), queue.active().stream())
                .filter(order -> order.timeWindowStart() != null && order.timeWindowEnd() != null)
                .filter(order -> order.timeWindowStart().isBefore(order.timeWindowEnd()))
                .map(order -> appointment(order, stops.get(order.orderId()), now))
                .toList();
        var measured = appointments.stream().filter(item -> item.bufferMinutes() != null)
                .sorted(Comparator.comparingLong(Appointment::bufferMinutes)).toList();
        var examples = measured.stream().limit(EXAMPLE_LIMIT).toList();
        var routes = examples.stream().collect(Collectors.toMap(Appointment::planId,
                item -> new Reference(item.planId(), item.orderCode()), (first, duplicate) -> first,
                LinkedHashMap::new)).values();
        return new Result(Map.of("asOfVietnam", local(now), "measured", measured.size(),
                "withoutEta", appointments.size() - measured.size(),
                "forecastLate", measured.stream().filter(item -> item.bufferMinutes() < 0).count(),
                "examplesLeastBufferFirst", examples), List.copyOf(routes));
    }

    @Transactional(readOnly = true)
    public Result routeHandoffs(AssistantScope scope) {
        List<DispatchPlan> plans = openPlans(scope);
        Instant now = Instant.now();
        var counts = plans.stream().collect(Collectors.groupingBy(AssistantToolData::stage, Collectors.counting()));
        var waitingForDriver = plans.stream()
                .filter(plan -> plan.getPlanStatus() == PlanStatus.DISPATCHED && plan.getAcknowledgedAt() == null)
                .sorted(Comparator.comparing(DispatchPlan::getDispatchedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(EXAMPLE_LIMIT)
                .toList();
        return new Result(Map.of("asOfVietnam", local(now), "stageCounts", counts,
                "awaitingDriverExamples", waitingForDriver.stream().map(plan -> Map.of("planId", plan.getId(),
                        "waitingMinutes", plan.getDispatchedAt() == null ? "unknown" : minutesSince(plan.getDispatchedAt(), now)))
                        .toList()), waitingForDriver.stream().map(AssistantToolData::reference).toList());
    }

    @Transactional(readOnly = true)
    public Result selectedRoute(AssistantScope scope) {
        var plan = planRepository.findByIdAndTenantId(scope.selectedPlanId(), scope.tenantId()).orElse(null);
        if (plan == null) return new Result(Map.of("selectedRoute", "unavailable"), List.of());
        return new Result(Map.of("planId", plan.getId(), "stage", stage(plan), "stopCount", plan.getOrders().size(),
                "distanceKm", plan.getTotalDistanceKm() == null ? "unknown" : plan.getTotalDistanceKm(),
                "estimatedDurationMinutes", Objects.toString(plan.getEstimatedDurationMinutes(), "unknown")),
                List.of(reference(plan)));
    }

    private List<DispatchPlan> openPlans(AssistantScope scope) {
        return planRepository.findAllByTenantIdAndPlanStatusIn(scope.tenantId(), OPEN_STAGES);
    }

    private static Reference reference(DispatchPlan plan) {
        return new Reference(plan.getId(), "#" + plan.getId().toString().substring(0, 8));
    }

    private void readStops(DispatchPlan plan, Map<UUID, Stop> stops) {
        Instant start = plan.getDispatchedAt() != null ? plan.getDispatchedAt()
                : plan.getPlannedAt() != null ? plan.getPlannedAt() : plan.getCreatedAt();
        if (start == null || plan.getRouteWaypoints() == null) return;
        try {
            double previous = 0;
            for (var point : json.readTree(plan.getRouteWaypoints())) {
                if (!point.path("estimated_arrival_minutes").isNumber()) continue;
                double eta = point.path("estimated_arrival_minutes").asDouble();
                if (!Double.isFinite(eta) || eta < previous || eta > 7 * 24 * 60) continue;
                try {
                    UUID id = UUID.fromString(point.path("stop_id").asText());
                    stops.put(id, new Stop(plan.getId(), start.plusMillis(Math.round(eta * 60_000))));
                    previous = eta;
                } catch (IllegalArgumentException ignored) {
                    // Older snapshots can include a depot marker instead of an order ID.
                }
            }
        } catch (JsonProcessingException ignored) {
            // Plans without a readable ETA stay in the unmeasured count.
        }
    }

    private static Appointment appointment(OrderResponse order, Stop stop, Instant now) {
        Instant deadline = order.timeWindowEnd().toInstant();
        String externalCode = order.externalOrderCode();
        String code = externalCode != null && externalCode.matches("[a-zA-Z0-9_-]{1,32}")
                ? externalCode : order.orderId().toString().substring(0, 8);
        if (stop == null) return new Appointment(code, null, local(deadline), null, null);
        Instant comparison = stop.eta().isBefore(now) ? now : stop.eta();
        long buffer = Math.floorDiv(Duration.between(comparison, deadline).toMillis(), 60_000);
        return new Appointment(code, stop.planId(), local(deadline), local(stop.eta()), buffer);
    }

    private static long minutesSince(Instant start, Instant now) {
        return start == null ? 0 : Math.max(0, Duration.between(start, now).toMinutes());
    }

    private static String depotLabel(String name) {
        return name == null || name.isBlank() ? "unknown" : name.substring(0, Math.min(80, name.length()));
    }

    private static String local(Instant value) {
        return value.atZone(VIETNAM).toOffsetDateTime().toString();
    }

    private static String stage(DispatchPlan plan) {
        if (plan.getPlanStatus() == PlanStatus.COMPLETED) return "COMPLETED";
        if (plan.getPlanStatus() == PlanStatus.DISPATCHED) {
            return plan.getAcknowledgedAt() == null ? "WAITING_DRIVER_ACKNOWLEDGMENT" : "DRIVER_ACKNOWLEDGED";
        }
        return plan.getDriverId() == null ? "UNASSIGNED" : "WAITING_TO_DISPATCH";
    }
}
