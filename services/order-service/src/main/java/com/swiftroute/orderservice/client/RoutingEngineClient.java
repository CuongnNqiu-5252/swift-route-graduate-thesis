package com.swiftroute.orderservice.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteInsertRequest;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteInsertResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteReoptimizeRequest;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteReoptimizeResponse;
import com.swiftroute.orderservice.grpc.routing.RoutingGrpcProtocol;

import lombok.extern.slf4j.Slf4j;

/**
 * Dual-mode client (gRPC primary + HTTP/1.1 fallback) for the SwiftRoute Routing Engine service.
 *
 * <p>Uses high-performance binary Protocol Buffers over gRPC HTTP/2 (port 50051) for
 * {@code OptimizeBatch}, {@code OptimizeInsert}, and {@code OptimizeReoptimize},
 * and automatically falls back to HTTP/1.1 REST (port 8084) if the gRPC endpoint
 * is temporarily unavailable.
 */
@Component
@Slf4j
public class RoutingEngineClient {

    private static final int MAX_RETRIES = 2;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String grpcHost;
    private final int grpcPort;
    private final boolean grpcEnabled;
    private final String internalKey;

    public RoutingEngineClient(String routingEngineUrl, String internalKey) {
        this(routingEngineUrl, 50051, "grpc", internalKey);
    }

    @Autowired
    public RoutingEngineClient(
            @Value("${routing-engine.url:http://localhost:8084}") String routingEngineUrl,
            @Value("${routing-engine.grpc-port:50051}") int grpcPort,
            @Value("${routing-engine.transport:grpc}") String transport,
            @Value("${tenant.internal-key:}") String internalKey) {
        this.internalKey = internalKey != null ? internalKey : "";
        this.grpcHost = extractHost(routingEngineUrl);
        this.grpcPort = grpcPort;
        this.grpcEnabled = !"http".equalsIgnoreCase(transport);

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(routingEngineUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        log.info("Initialized RoutingEngineClient (transport={}, grpcTarget={}:{}, httpFallback={})",
                this.grpcEnabled ? "gRPC" : "HTTP", this.grpcHost, this.grpcPort, routingEngineUrl);
    }

    private static String extractHost(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost();
            }
        } catch (Exception ignored) {
        }
        return "localhost";
    }

    /**
     * Submits a batch optimisation request to the routing engine via gRPC (with HTTP fallback).
     *
     * @param request the optimisation request with depot + stops + weights
     * @return the routing engine response, or empty on permanent failure
     */
    public Optional<BatchOptimizationResponse> optimizeBatch(BatchOptimizationRequest request) {
        if (grpcEnabled) {
            try {
                BatchOptimizationResponse response = RoutingGrpcProtocol.callOptimizeBatch(
                        grpcHost, grpcPort, request, internalKey, TIMEOUT);
                log.info("⚡ [gRPC Client] OptimizeBatch succeeded jobId={} algorithm={} totalDistanceKm={} executionTimeMs={}",
                        response != null ? response.jobId() : "null",
                        response != null ? response.algorithmUsed() : "null",
                        response != null ? response.totalDistanceKm() : "null",
                        response != null ? response.executionTimeMs() : "null");
                return Optional.ofNullable(response);
            } catch (IOException grpcEx) {
                log.warn("gRPC OptimizeBatch call to {}:{} failed ({}), falling back to HTTP/1.1 REST",
                        grpcHost, grpcPort, grpcEx.getMessage());
            }
        }

        int attempt = 0;
        while (attempt <= MAX_RETRIES) {
            try {
                BatchOptimizationResponse response = restClient.post()
                        .uri("/optimize/batch")
                        .header("X-Internal-Key", internalKey)
                        .header("X-Tenant-Id", "system")
                        .body(request)
                        .retrieve()
                        .body(BatchOptimizationResponse.class);
                log.info("Routing engine (HTTP) returned plan jobId={} algorithm={} totalDistanceKm={}",
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
     * Submits a dynamic stop-insertion request via gRPC (with HTTP fallback) (P3.1).
     */
    public Optional<RouteInsertResponse> optimizeInsert(RouteInsertRequest request) {
        if (grpcEnabled) {
            try {
                RouteInsertResponse response = RoutingGrpcProtocol.callOptimizeInsert(
                        grpcHost, grpcPort, request, internalKey, TIMEOUT);
                log.info("⚡ [gRPC Client] OptimizeInsert succeeded jobId={} feasible={} insertedStopIndex={}",
                        response != null ? response.jobId() : "null",
                        response != null ? response.feasible() : "null",
                        response != null ? response.insertedStopIndex() : "null");
                return Optional.ofNullable(response);
            } catch (IOException grpcEx) {
                log.warn("gRPC OptimizeInsert call failed ({}), falling back to HTTP/1.1 REST",
                        grpcEx.getMessage());
            }
        }

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
     * Submits a safe remaining-stops reoptimization request via gRPC (with HTTP fallback) (P3.2).
     */
    public Optional<RouteReoptimizeResponse> optimizeReoptimize(RouteReoptimizeRequest request) {
        if (grpcEnabled) {
            try {
                RouteReoptimizeResponse response = RoutingGrpcProtocol.callOptimizeReoptimize(
                        grpcHost, grpcPort, request, internalKey, TIMEOUT);
                log.info("⚡ [gRPC Client] OptimizeReoptimize succeeded jobId={} stopsChanged={} distanceSavedKm={}",
                        response != null ? response.jobId() : "null",
                        response != null ? response.stopsChanged() : "null",
                        response != null ? response.distanceSavedKm() : "null");
                return Optional.ofNullable(response);
            } catch (IOException grpcEx) {
                log.warn("gRPC OptimizeReoptimize call failed ({}), falling back to HTTP/1.1 REST",
                        grpcEx.getMessage());
            }
        }

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
