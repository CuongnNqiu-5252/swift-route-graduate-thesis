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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Calls Routing Engine over gRPC for batch planning. Dynamic routing supports
 * configurable transport and HTTP fallback. Ordered-route measurement uses REST.
 */
@Component
@Slf4j
public class RoutingEngineClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final ManagedChannel batchChannel;
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
        this.batchChannel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort).usePlaintext().build();

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

        log.info("Initialized RoutingEngineClient (dynamicTransport={}, grpcTarget={}:{}, restTarget={})",
                this.grpcEnabled ? "gRPC" : "HTTP", this.grpcHost, this.grpcPort, routingEngineUrl);
    }

    @PreDestroy
    void shutdownBatchChannel() {
        batchChannel.shutdownNow();
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
     * Submits a batch optimisation request to the routing engine via gRPC.
     *
     * @param request the optimisation request with depot + stops + weights
     * @return the routing engine response, or empty on permanent failure
     */
    public Optional<BatchOptimizationResponse> optimizeBatch(BatchOptimizationRequest request) {
        try {
            BatchOptimizationResponse response = RoutingGrpcProtocol.callOptimizeBatch(
                    batchChannel, request, internalKey, TIMEOUT);
            if (request.vehicleCount() != null && request.vehicleCount() > 1
                    && !response.orderedStops().isEmpty() && response.vehicleRoutes().isEmpty()) {
                log.error("gRPC OptimizeBatch at {}:{} returned stops without per-vehicle routes",
                        grpcHost, grpcPort);
                return Optional.empty();
            }
            log.info("gRPC OptimizeBatch succeeded jobId={} algorithm={} routes={} executionTimeMs={}",
                    response.jobId(), response.algorithmUsed(), response.vehicleRoutes().size(),
                    response.executionTimeMs());
            return Optional.of(response);
        } catch (IOException | StatusRuntimeException ex) {
            log.error("gRPC OptimizeBatch call to {}:{} failed: {}", grpcHost, grpcPort, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<BatchOptimizationResponse> measureOrderedRoute(BatchOptimizationRequest request) {
        try {
            return Optional.ofNullable(restClient.post()
                    .uri("/optimize/measure")
                    .header("X-Internal-Key", internalKey)
                    .header("X-Tenant-Id", "system")
                    .body(request)
                    .retrieve()
                    .body(BatchOptimizationResponse.class));
        } catch (RestClientException ex) {
            log.warn("Routing engine could not measure the ordered route: {}", ex.getMessage());
            return Optional.empty();
        }
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
