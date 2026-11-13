package com.swiftroute.driverservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Queries Goong Direction API with vehicle=bike to obtain the exact road distance
 * (accounting for one-way streets, bridges, and motorcycle navigation constraints).
 */
@Service
@Slf4j
public class GoongRoadDistanceService {

    private final RestClient restClient;
    private final String apiKey;
    private final boolean enabled;

    public GoongRoadDistanceService(
            @Value("${goong.api-key:}") String apiKey,
            @Value("${goong.base-url:https://rsapi.goong.io}") String baseUrl) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.enabled = !this.apiKey.isEmpty();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        if (this.enabled) {
            log.info("GoongRoadDistanceService initialized with Goong Direction API (bike mode).");
        } else {
            log.info("GoongRoadDistanceService: GOONG_API_KEY is not configured; will fallback to geodesic distance.");
        }
    }

    /**
     * Resolves the motorcycle road distance in kilometers using Goong Directions.
     *
     * @return exact road distance in kilometers (scale 2, HALF_UP), or Optional.empty() if disabled or unavailable.
     */
    public Optional<BigDecimal> getBikeRoadDistanceKm(Double originLat, Double originLng,
            Double destLat, Double destLng) {
        if (!enabled || originLat == null || originLng == null || destLat == null || destLng == null) {
            return Optional.empty();
        }

        try {
            String origin = String.format(Locale.US, "%.6f,%.6f", originLat, originLng);
            String destination = String.format(Locale.US, "%.6f,%.6f", destLat, destLng);

            JsonNode root = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Direction")
                            .queryParam("origin", origin)
                            .queryParam("destination", destination)
                            .queryParam("vehicle", "bike")
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (root != null && root.has("routes") && root.get("routes").isArray() && !root.get("routes").isEmpty()) {
                JsonNode route = root.get("routes").get(0);
                if (route.has("legs") && route.get("legs").isArray() && !route.get("legs").isEmpty()) {
                    JsonNode leg = route.get("legs").get(0);
                    if (leg.has("distance") && leg.get("distance").has("value")) {
                        long meters = leg.get("distance").get("value").asLong();
                        if (meters > 0) {
                            BigDecimal km = BigDecimal.valueOf(meters)
                                    .divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
                            log.info("Goong Direction (bike): ({}) -> ({}) = {} meters ({} km)",
                                    origin, destination, meters, km);
                            return Optional.of(km);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve road distance from Goong: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
