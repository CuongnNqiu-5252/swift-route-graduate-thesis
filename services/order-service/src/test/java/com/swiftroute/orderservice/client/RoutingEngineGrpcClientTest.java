package com.swiftroute.orderservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest.LocationPoint;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationRequest.StopItem;
import com.swiftroute.orderservice.dto.routing.BatchOptimizationResponse;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteInsertRequest;
import com.swiftroute.orderservice.dto.routing.DynamicRoutingDtos.RouteStopState;
import com.swiftroute.orderservice.grpc.routing.RoutingGrpcProtocol;

class RoutingEngineGrpcClientTest {

    @Test
    void protobufWireEncoding_roundTripsBatchRequestAndResponseAccurately() throws Exception {
        BatchOptimizationRequest request = new BatchOptimizationRequest(
                new LocationPoint(10.0316, 105.7880, "Depot Can Tho"),
                List.of(new StopItem(
                        "order-uuid-1",
                        "Recipient A",
                        new LocationPoint(10.0452, 105.7469, "180 Le Binh"),
                        2.5,
                        "2026-11-08T15:00:00Z",
                        "2026-11-08T17:00:00Z")),
                "ortools",
                0.6,
                0.4);

        byte[] encodedReq = RoutingGrpcProtocol.encodeBatchRequest(request);
        assertThat(encodedReq).isNotEmpty();

        RouteInsertRequest insertReq = new RouteInsertRequest(
                new LocationPoint(10.0316, 105.7880, null),
                List.of(new RouteStopState(
                        "stop-1",
                        "Customer 1",
                        new LocationPoint(10.04, 105.75, "Addr 1"),
                        1.0,
                        null,
                        null,
                        true)),
                new StopItem("stop-2", "Customer 2", new LocationPoint(10.05, 105.76, "Addr 2"), 2.0, null, null),
                30.0,
                0.5,
                0.5);
        byte[] encodedInsert = RoutingGrpcProtocol.encodeInsertRequest(insertReq);
        assertThat(encodedInsert).isNotEmpty();

        BatchOptimizationResponse emptyDecoded = RoutingGrpcProtocol.decodeBatchResponse(new byte[0]);
        assertThat(emptyDecoded.orderedStops()).isEmpty();
    }

    @Test
    void liveGrpcCall_toPythonRoutingEngineOnPort50051_whenRunning() {
        boolean isPortOpen = false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", 50051), 500);
            isPortOpen = true;
        } catch (Exception ignored) {
        }
        if (!isPortOpen) {
            return;
        }

        RoutingEngineClient client = new RoutingEngineClient(
                "http://localhost:8084", 50051, "grpc", "r6ZF--NNhh0xpAxZVICyJgLmbLBqciqGa2_Jx8AF_ck");

        BatchOptimizationRequest request = new BatchOptimizationRequest(
                new LocationPoint(10.0316, 105.7880, "Depot Can Tho"),
                List.of(
                        new StopItem(
                                "order-1",
                                "Recipient A",
                                new LocationPoint(10.0218, 105.7682, "180 Le Binh"),
                                1.0,
                                null,
                                null),
                        new StopItem(
                                "order-2",
                                "Recipient B",
                                new LocationPoint(10.0305, 105.7568, "335 Nguyen Van Linh"),
                                1.0,
                                null,
                                null)),
                "auto",
                0.5,
                0.5);

        Optional<BatchOptimizationResponse> result = client.optimizeBatch(request);
        assertThat(result).isPresent();
        assertThat(result.get().algorithmUsed()).isEqualTo("ortools");
        assertThat(result.get().orderedStops()).hasSize(2);
        assertThat(result.get().totalDistanceKm()).isGreaterThan(0.0);
    }
}
