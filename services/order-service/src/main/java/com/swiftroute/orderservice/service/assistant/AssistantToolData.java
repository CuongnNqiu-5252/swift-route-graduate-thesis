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
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.service.weather.RouteWeatherService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.service.OrderService;
import com.swiftroute.orderservice.service.planning.PlanningEligibilityEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssistantToolData {
    private static final int EXAMPLE_LIMIT = 8;
    private static final int RISK_BUFFER_MINUTES = 15;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<PlanStatus> OPEN_STAGES = List.of(PlanStatus.DRAFT, PlanStatus.APPROVED, PlanStatus.DISPATCHED);

    private final OrderService orderService;
    private final DispatchPlanRepository planRepository;
    private final ObjectMapper json;
    private final RouteWeatherService weather;
    private final PlanningEligibilityEvaluator eligibility;

    public record Reference(@JsonProperty("planId") UUID planId, String label) {}
    public record PlanningDepot(@JsonProperty("depot_id") UUID depotId,
            @JsonProperty("depot_name") String depotName, @JsonProperty("order_count") long orderCount) {}

    public record Result(Map<String, Object> facts, List<Reference> routes,
            @JsonProperty("planning_depots") List<PlanningDepot> planningDepots,
            @JsonProperty("risky_routes") List<Reference> riskyRoutes) {
        public Result {
            planningDepots = planningDepots == null ? List.of() : planningDepots;
            riskyRoutes = riskyRoutes == null ? List.of() : riskyRoutes;
        }

        public Result(Map<String, Object> facts, List<Reference> routes) {
            this(facts, routes, List.of(), List.of());
        }
    }

    public Result capabilities(AssistantScope scope) {
        return new Result(Map.of("asOfVietnam", local(Instant.now()), "timezone", VIETNAM.toString(),
                "readOrders", scope.readOrders(), "readPlans", scope.readPlans(),
                "workflows", List.of(OrderCreationGuide.WORKFLOW,
                        "Dispatch: plan, assign a driver, dispatch, physical pickup, deliveries",
                        "Operations: investigate failed deliveries and returns, COD settlement",
                        "Weather: saved ETA forecast, rain >=40%, tight deadline buffer <=15 minutes"),
                "limits", List.of("Read-only; cannot create, cancel, assign or dispatch",
                        "No live driver location or traffic-delay tool", "Weather never changes ETA",
                        "Tool counts refer to matching records; examples and pages may be incomplete")), List.of());
    }

    @Transactional(readOnly = true)
    public Result searchOrders(AssistantScope scope, String query, String status, int page, int limit) {
        var result = orderService.getOrderPage(scope.tenantId(),
                status == null || status.isBlank() ? null : OrderStatus.valueOf(status.toUpperCase(Locale.ROOT)),
                null, query, List.of(), page, limit);
        var orders = result.orders();
        return new Result(Map.of("asOfVietnam", local(Instant.now()), "totalMatches", orders.totalElements(),
                "page", page, "hasMore", !orders.isLast(), "orders", orders.content().stream().map(order -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("orderId", order.orderId());
                    item.put("orderCode", order.externalOrderCode());
                    item.put("status", order.status());
                    item.put("pickupLocation", order.pickupLocationName());
                    item.put("appointmentStart", order.timeWindowStart());
                    item.put("appointmentEnd", order.timeWindowEnd());
                    item.put("driverAssigned", order.assignedDriverId() != null);
                    return item;
                }).toList()), List.of());
    }

    @Transactional(readOnly = true)
    public Result listRoutes(AssistantScope scope, int page, int limit) {
        var result = planRepository.findByTenantId(scope.tenantId(),
                PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new Result(Map.of("asOfVietnam", local(Instant.now()), "totalMatches", result.getTotalElements(),
                "page", page, "hasMore", result.hasNext(), "routes", result.getContent().stream()
                        .map(plan -> Map.of("planId", plan.getId(), "stage", stage(plan), "stopCount", plan.getOrders().size(),
                                "driverAssigned", plan.getDriverId() != null)).toList()),
                result.getContent().stream().map(AssistantToolData::reference).toList());
    }

    @Transactional(readOnly = true)
    public Result routeDetails(AssistantScope scope, UUID planId) {
        var plan = planRepository.findByIdAndTenantId(planId, scope.tenantId()).orElse(null);
        if (plan == null) return new Result(Map.of("route", "unavailable"), List.of());
        Instant now = Instant.now();
        Map<UUID, Stop> timings = new HashMap<>();
        readStops(plan, timings, now);
        var routeStops = plan.getOrdersInStopOrder();
        List<Map<String, Object>> stops = new ArrayList<>();
        routeStops.stream().limit(50).forEach(order -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", order.getId());
            item.put("orderCode", order.getExternalOrderCode());
            item.put("status", order.getStatus());
            item.put("eta", timings.containsKey(order.getId()) ? local(timings.get(order.getId()).eta()) : null);
            item.put("deadline", order.getTimeWindowEnd());
            stops.add(item);
        });
        return new Result(Map.of("asOfVietnam", local(now), "planId", planId, "stage", stage(plan),
                "totalStops", routeStops.size(), "truncated", routeStops.size() > 50, "stops", stops,
                "distanceKm", Objects.toString(plan.getTotalDistanceKm(), "unknown"),
                "estimatedDurationMinutes", Objects.toString(plan.getEstimatedDurationMinutes(), "unknown")), List.of(reference(plan)));
    }

    public Result weatherRisks(AssistantScope scope, UUID planId, String period) {
        var report = weather.evaluate(scope.tenantId(), planId, period);
        var summary = new LinkedHashMap<String, Object>();
        summary.put("source", report.source());
        summary.put("asOf", report.asOf());
        summary.put("period", report.period());
        summary.put("from", report.from());
        summary.put("to", report.to());
        summary.put("evaluatedStops", report.evaluatedStops());
        summary.put("unknownStops", report.unknownStops());
        summary.put("outsidePeriodStops", report.outsidePeriodStops());
        summary.put("affectedRoutes", report.routes().size());
        summary.put("affectedStops", report.routes().stream().mapToInt(route -> route.stops().size()).sum());
        summary.put("urgentStops", report.routes().stream().mapToInt(RouteWeatherService.RouteRisk::urgentStops).sum());
        summary.put("routeExamples", report.routes().stream().limit(12).map(route -> Map.of(
                "planId", route.planId(), "affectedStops", route.stops().size(), "urgentStops", route.urgentStops(),
                "stopExamples", route.stops().stream().limit(8).toList())).toList());
        summary.put("examplesMayBeTruncated", true);
        var routes = report.routes().stream().limit(20).map(route -> new Reference(route.planId(),
                "#" + route.planId().toString().substring(0, 8))).toList();
        return new Result(Map.of("weather", summary, "interpretation",
                "Probability is not rain intensity or delay. Unknown/stale ETA and unavailable forecast are not safe-weather evidence. "
                        + "Urgent means rain at saved ETA plus <=15-minute appointment buffer; it does not predict extra travel time."),
                routes, List.of(), routes);
    }
    private record DepotBacklog(@JsonProperty("depot_id") UUID depotId,
            @JsonProperty("depot_name") String depotName, int waiting,
            @JsonProperty("oldest_minutes") long oldestMinutes) {}
    private record Stop(UUID planId, Instant eta) {}
    private record Appointment(String orderCode, UUID planId, String deadline, String eta, Long bufferMinutes) {}

    @Transactional(readOnly = true)
    public Result waitingOrders(AssistantScope scope) {
        var queue = orderService.getOperationsQueue(scope.tenantId(), scope.userId(), scope.role());
        Instant now = Instant.now();
        var groups = queue.readyForDispatch().stream().collect(Collectors.groupingBy(
                order -> Objects.toString(order.pickupLocationId(), "unknown")));
        var depots = groups.values().stream()
                .map(group -> new DepotBacklog(group.getFirst().pickupLocationId(),
                        depotLabel(group.getFirst().pickupLocationName()),
                        group.size(), group.stream().map(OrderResponse::createdAt)
                                .filter(Objects::nonNull).mapToLong(created -> minutesSince(created, now)).max().orElse(0)))
                .sorted(Comparator.comparingLong(DepotBacklog::oldestMinutes).reversed()
                        .thenComparing(DepotBacklog::depotId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<PlanningDepot> planningDepots = List.of();
        if (scope.executeDispatch()) {
            var eligibleByDepot = eligibility.getEligibleOrders(scope.tenantId(), now).stream()
                    .map(Order::getPickupLocation).filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(location -> location.getId(), Collectors.counting()));
            planningDepots = depots.stream()
                    .filter(depot -> depot.depotId() != null && eligibleByDepot.containsKey(depot.depotId()))
                    .limit(EXAMPLE_LIMIT)
                    .map(depot -> new PlanningDepot(depot.depotId(), depot.depotName(), eligibleByDepot.get(depot.depotId())))
                    .toList();
        }
        return new Result(Map.of("asOfVietnam", local(now), "totalWaiting", queue.readyForDispatch().size(),
                "totalDepots", groups.size(), "depotExamplesOldestFirst", depots.stream().limit(EXAMPLE_LIMIT).toList()),
                List.of(), planningDepots, List.of());
    }

    @Transactional(readOnly = true)
    public Result appointmentRisks(AssistantScope scope) {
        var queue = orderService.getOperationsQueue(scope.tenantId(), scope.userId(), scope.role());
        Instant now = Instant.now();
        Map<UUID, Stop> stops = new HashMap<>();
        openPlans(scope).forEach(plan -> readStops(plan, stops, now));
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
        var riskyIds = examples.stream().filter(item -> item.bufferMinutes() <= RISK_BUFFER_MINUTES)
                .map(Appointment::planId).collect(Collectors.toSet());
        var riskyRoutes = routes.stream().filter(route -> riskyIds.contains(route.planId()))
                .map(route -> new Reference(route.planId(), "#" + route.planId().toString().substring(0, 8))).toList();
        return new Result(Map.of("asOfVietnam", local(now), "measured", measured.size(),
                "withoutEta", appointments.size() - measured.size(),
                "forecastLate", measured.stream().filter(item -> item.bufferMinutes() < 0).count(),
                "tightBuffer", measured.stream()
                        .filter(item -> item.bufferMinutes() >= 0 && item.bufferMinutes() <= RISK_BUFFER_MINUTES).count(),
                "examplesLeastBufferFirst", examples), List.copyOf(routes), List.of(), riskyRoutes);
    }

    @Transactional(readOnly = true)
    public Result routeHandoffs(AssistantScope scope) {
        var counts = openPlans(scope).stream().collect(Collectors.groupingBy(
                AssistantToolData::stage, Collectors.counting()));
        return new Result(Map.of("asOfVietnam", local(Instant.now()), "stageCounts", counts), List.of());
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

    private void readStops(DispatchPlan plan, Map<UUID, Stop> stops, Instant now) {
        Instant start;
        if (plan.getDispatchedAt() != null) {
            start = plan.getDispatchedAt();
        } else {
            Instant created = plan.getCreatedAt();
            start = created != null && now != null && created.isBefore(now) ? now : created;
        }
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
        if (plan.getPlanStatus() == PlanStatus.DISPATCHED) return "DISPATCHED";
        return plan.getDriverId() == null ? "UNASSIGNED" : "WAITING_TO_DISPATCH";
    }
}
