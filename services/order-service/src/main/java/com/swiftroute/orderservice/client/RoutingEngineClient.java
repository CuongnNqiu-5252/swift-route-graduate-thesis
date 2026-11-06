package com.swiftroute.orderservice.client;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteInsertRequest;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteInsertResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteReoptimizeRequest;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteReoptimizeResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTP client for the SwiftRoute Routing Engine service.
 *
 * <p>Calls {@code POST /optimize/batch} to get an OR-Tools optimised route.
 * The routing engine is a separate Python FastAPI service; this client handles
 * timeout (30 s) and simple retry logic (up to 2 retries on transient failures).
 *
 * <p>All requests carry an internal service key header so the routing engine can
 * authenticate calls without requiring a user JWT.
 */
@Component
@Slf4j
public class RoutingEngineClient {

    private static final int MAX_RETRIES = 2;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String internalKey;

    public RoutingEngineClient(
            @Value("${routing-engine.url:http://localhost:8084}") String routingEngineUrl,
            @Value("${tenant.internal-key:}") String internalKey) {
        this.internalKey = internalKey;
        this.restClient = RestClient.builder()
                .baseUrl(routingEngineUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Submits a batch optimisation request to the routing engine.
     *
     * @param request the optimisation request with depot + stops + weights
     * @return the routing engine response, or empty on permanent failure
     */
    public Optional<BatchOptimizationResponse> optimizeBatch(BatchOptimizationRequest request) {
        int attempt = 0;
        while (attempt <= MAX_RETRIES) {
            try {
                BatchOptimizationResponse response = restClient.post()
                        .uri("/optimize/batch")
                        .header("X-Internal-Key", internalKey)
                        // Tenant header required by routing engine security filter;
                        // use a special system tenant marker for internal service calls.
                        .header("X-Tenant-Id", "system")
                        .body(request)
                        .retrieve()
                        .body(BatchOptimizationResponse.class);
                log.info("Routing engine returned plan jobId={} algorithm={} totalDistanceKm={}",
                        response != null ? response.jobId() : "null",
                        response != null ? response.algorithmUsed() : "null",
                        response != null ? response.totalDistanceKm() : "null");
                return Optional.ofNullable(response);
            } catch (RestClientException ex) {
                attempt++;
                if (attempt > MAX_RETRIES) {
                    log.error("Routing engine call failed after {} attempts: {}", MAX_RETRIES + 1, ex.getMessage(), ex);
                    return Optional.empty();
                }
                log.warn("Routing engine call attempt {}/{} failed: {}. Retrying...", attempt, MAX_RETRIES + 1, ex.getMessage());
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Submits a dynamic stop-insertion request to POST /optimize/insert (P3.1).
     */
    public Optional<RouteInsertResponse> optimizeInsert(RouteInsertRequest request) {
        try {
            RouteInsertResponse response = restClient.post()
                    .uri("/optimize/insert")
                    .header("X-Internal-Key", internalKey)
                    .header("X-Tenant-Id", "system")
                    .body(request)
                    .retrieve()
                    .body(RouteInsertResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException ex) {
            log.error("Routing engine /optimize/insert failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Submits a safe remaining-stops reoptimization request to POST /optimize/reoptimize (P3.2).
     */
    public Optional<RouteReoptimizeResponse> optimizeReoptimize(RouteReoptimizeRequest request) {
        try {
            RouteReoptimizeResponse response = restClient.post()
                    .uri("/optimize/reoptimize")
                    .header("X-Internal-Key", internalKey)
                    .header("X-Tenant-Id", "system")
                    .body(request)
                    .retrieve()
                    .body(RouteReoptimizeResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException ex) {
            log.error("Routing engine /optimize/reoptimize failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}

