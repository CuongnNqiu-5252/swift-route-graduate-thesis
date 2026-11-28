package com.swiftroute.orderservice.service.weather;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RouteWeatherService {
    public static final int RAIN_THRESHOLD = 40;
    public static final int TIGHT_BUFFER_MINUTES = 15;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final WeatherPlanData data;
    private final RainForecastClient forecasts;

    public record AffectedStop(UUID orderId, String orderCode, Instant eta, int rainProbability,
            Long bufferMinutes, boolean urgent, Instant forecastFetchedAt) {}
    public record RouteRisk(UUID planId, List<AffectedStop> stops, int urgentStops) {}
    public record Report(String source, Instant asOf, String period, Instant from, Instant to,
            int rainThreshold, int tightBufferMinutes, int evaluatedStops, int unknownStops,
            int outsidePeriodStops, List<RouteRisk> routes) {}

    public Report evaluate(UUID tenantId, UUID planId, String period) {
        Instant now = Instant.now();
        return evaluate(data.load(tenantId, planId, now), period, now);
    }

    Report evaluate(List<WeatherPlanData.Stop> stops, String period, Instant now) {
        var interval = interval(period, now);
        var candidates = stops.stream().filter(stop -> stop.eta() != null && !stop.eta().isBefore(interval[0])
                && stop.eta().isBefore(interval[1]) && validCoordinate(stop)).toList();
        var values = forecasts.forecasts(candidates.stream().map(stop -> RainForecastClient.Point.of(stop.lat(), stop.lng())).toList(), now);
        Map<UUID, List<AffectedStop>> routes = new LinkedHashMap<>();
        int evaluated = 0;
        int unknown = (int) stops.stream().filter(stop -> stop.eta() == null || stop.eta().isBefore(now)
                || (!validCoordinate(stop) && !stop.eta().isBefore(interval[0]) && stop.eta().isBefore(interval[1]))).count();
        for (var stop : candidates) {
            var forecast = values.get(RainForecastClient.Point.of(stop.lat(), stop.lng()));
            Integer probability = forecast == null || !forecast.fetchedAt().isAfter(now.minusSeconds(600))
                    ? null : forecast.at(stop.eta());
            if (probability == null) { unknown++; continue; }
            evaluated++;
            if (probability < RAIN_THRESHOLD) continue;
            Long buffer = stop.deadline() == null ? null : Math.floorDiv(Duration.between(stop.eta(), stop.deadline()).toSeconds(), 60);
            boolean urgent = buffer != null && buffer <= TIGHT_BUFFER_MINUTES;
            routes.computeIfAbsent(stop.planId(), key -> new ArrayList<>()).add(new AffectedStop(stop.orderId(),
                    stop.orderCode(), stop.eta(), probability, buffer, urgent, forecast.fetchedAt()));
        }
        int outside = stops.size() - evaluated - unknown;
        return new Report("Open-Meteo", now, period, interval[0], interval[1], RAIN_THRESHOLD,
                TIGHT_BUFFER_MINUTES, evaluated, unknown, Math.max(0, outside), routes.entrySet().stream()
                .map(entry -> new RouteRisk(entry.getKey(), List.copyOf(entry.getValue()),
                        (int) entry.getValue().stream().filter(AffectedStop::urgent).count()))
                .sorted(Comparator.comparingInt(RouteRisk::urgentStops).reversed()).toList());
    }

    static Instant[] interval(String period, Instant now) {
        var date = now.atZone(VIETNAM).toLocalDate();
        return switch (period) {
            case "today_afternoon" -> {
                Instant end = date.atTime(18, 0).atZone(VIETNAM).toInstant();
                yield new Instant[] {now.isAfter(end) ? end : max(now, date.atTime(12, 0).atZone(VIETNAM).toInstant()), end};
            }
            case "today" -> new Instant[] {now, date.plusDays(1).atStartOfDay(VIETNAM).toInstant()};
            case "tomorrow" -> new Instant[] {date.plusDays(1).atStartOfDay(VIETNAM).toInstant(), date.plusDays(2).atStartOfDay(VIETNAM).toInstant()};
            case "next_24_hours" -> new Instant[] {now, now.plus(Duration.ofHours(24))};
            default -> throw new IllegalArgumentException("Unsupported forecast period");
        };
    }

    private static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
    private static boolean validCoordinate(WeatherPlanData.Stop stop) {
        return stop.lat() != null && stop.lng() != null && Double.isFinite(stop.lat()) && Double.isFinite(stop.lng())
                && Math.abs(stop.lat()) <= 90 && Math.abs(stop.lng()) <= 180;
    }
}
