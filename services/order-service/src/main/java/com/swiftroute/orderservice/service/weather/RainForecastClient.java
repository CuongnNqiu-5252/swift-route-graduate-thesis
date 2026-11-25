package com.swiftroute.orderservice.service.weather;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RainForecastClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final String apiKey;
    private final Map<Point, Forecast> cache = new ConcurrentHashMap<>();
    public record Point(double lat, double lng) {
        public static Point of(double lat, double lng) {
            return new Point(Math.round(lat * 1000) / 1000.0, Math.round(lng * 1000) / 1000.0);
        }
    }
    public record Forecast(Instant fetchedAt, Map<Long, Integer> hours) {
        public Integer at(Instant eta) {
            long hourEnd = (Math.floorDiv(eta.getEpochSecond(), 3600) + 1) * 3600;
            return hours.get(hourEnd);
        }
    }

    public RainForecastClient(ObjectMapper json, @Value("${OPEN_METEO_API_KEY:}") String apiKey) {
        this.json = json;
        this.apiKey = apiKey.trim();
    }

    /** At most 100 unique coordinates and ten parallel requests per evaluation. */
    public Map<Point, Forecast> forecasts(List<Point> requested, Instant now) {
        var points = requested.stream().distinct().limit(100).toList();
        Map<Point, Forecast> result = new HashMap<>();
        List<Point> missing = new ArrayList<>();
        for (var point : points) {
            var stored = cache.get(point);
            if (stored != null && stored.fetchedAt().isAfter(now.minusSeconds(600))) result.put(point, stored);
            else missing.add(point);
        }
        List<CompletableFuture<Map<Point, Forecast>>> pending = new ArrayList<>();
        for (int offset = 0; offset < missing.size(); offset += 10) {
            var batch = List.copyOf(missing.subList(offset, Math.min(offset + 10, missing.size())));
            String lat = batch.stream().map(p -> Double.toString(p.lat())).collect(Collectors.joining(","));
            String lng = batch.stream().map(p -> Double.toString(p.lng())).collect(Collectors.joining(","));
            String endpoint = apiKey.isEmpty() ? "https://api.open-meteo.com/v1/forecast"
                    : "https://customer-api.open-meteo.com/v1/forecast";
            String url = endpoint + "?latitude=" + lat + "&longitude=" + lng
                    + "&hourly=precipitation_probability&timeformat=unixtime&forecast_days=3&timezone=Asia%2FHo_Chi_Minh"
                    + (apiKey.isEmpty() ? "" : "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build();
            pending.add(http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .<Map<Point, Forecast>>handle((response, failure) -> {
                        if (failure != null || response.statusCode() != 200) return Map.of();
                        try { return parse(batch, json.readTree(response.body()), now); }
                        catch (Exception ignored) { return Map.of(); }
                    }));
        }
        try { CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(8, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (Exception ignored) { }
        for (var request : pending) {
            if (request.isDone() && !request.isCompletedExceptionally()) result.putAll(request.getNow(Map.of()));
            else request.cancel(true);
        }
        if (cache.size() > 2000) cache.clear();
        cache.putAll(result);
        return Map.copyOf(result);
    }

    static Map<Point, Forecast> parse(List<Point> points, JsonNode payload, Instant fetchedAt) {
        List<JsonNode> responses = new ArrayList<>();
        if (payload.isArray()) payload.forEach(responses::add); else responses.add(payload);
        if (responses.size() != points.size()) return Map.of();
        Map<Point, Forecast> result = new LinkedHashMap<>();
        for (int i = 0; i < points.size(); i++) {
            var hourly = responses.get(i).path("hourly");
            var times = hourly.path("time");
            var probabilities = hourly.path("precipitation_probability");
            if (!times.isArray() || !probabilities.isArray() || times.size() != probabilities.size()) continue;
            Map<Long, Integer> hours = new HashMap<>();
            for (int j = 0; j < times.size(); j++) {
                var value = probabilities.get(j);
                if (times.get(j).isIntegralNumber() && value.isNumber() && value.asDouble() >= 0 && value.asDouble() <= 100) {
                    hours.put(times.get(j).asLong(), (int) Math.round(value.asDouble()));
                }
            }
            if (!hours.isEmpty()) result.put(points.get(i), new Forecast(fetchedAt, Map.copyOf(hours)));
        }
        return result;
    }
}
