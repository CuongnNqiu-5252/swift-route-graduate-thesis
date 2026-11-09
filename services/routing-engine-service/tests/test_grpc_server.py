# tests/test_grpc_server.py
"""
Integration tests for the RoutingEngineGrpcService (OptimizeBatch, OptimizeInsert, OptimizeReoptimize, SimulateWhatIf).
"""
from __future__ import annotations

import grpc
import pytest

from api.grpc_gen import routing_pb2, routing_pb2_grpc
from service.grpc_server import start_grpc_server, stop_grpc_server


@pytest.fixture(scope="module")
def grpc_channel():
    port = 50099
    start_grpc_server(port=port)
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield channel
    channel.close()
    stop_grpc_server()


def test_grpc_optimize_batch(grpc_channel):
    stub = routing_pb2_grpc.RoutingEngineGrpcServiceStub(grpc_channel)
    request = routing_pb2.BatchOptimizationGrpcRequest(
        depot=routing_pb2.LocationPointProto(lat=10.0316, lng=105.7880, address="Depot Can Tho"),
        stops=[
            routing_pb2.StopItemProto(
                id="order-1",
                name="Customer A",
                location=routing_pb2.LocationPointProto(lat=10.0452, lng=105.7469, address="Stop 1"),
                demand_kg=2.0,
            ),
            routing_pb2.StopItemProto(
                id="order-2",
                name="Customer B",
                location=routing_pb2.LocationPointProto(lat=10.0150, lng=105.7650, address="Stop 2"),
                demand_kg=1.5,
            ),
        ],
        algorithm="ortools",
        time_weight=0.5,
        cost_weight=0.5,
        tenant_id="tenant-test-1",
    )
    response = stub.OptimizeBatch(request, timeout=10.0)
    assert response.job_id != ""
    assert response.algorithm_used == "ortools"
    assert response.total_distance_km > 0
    assert response.total_duration_minutes > 0
    assert len(response.ordered_stops) == 2
    assert {s.stop_id for s in response.ordered_stops} == {"order-1", "order-2"}


def test_grpc_optimize_insert(grpc_channel):
    stub = routing_pb2_grpc.RoutingEngineGrpcServiceStub(grpc_channel)
    request = routing_pb2.RouteInsertGrpcRequest(
        driver_location=routing_pb2.LocationPointProto(lat=10.0316, lng=105.7880),
        current_stops=[
            routing_pb2.RouteStopStateProto(
                id="order-1",
                name="Completed Stop",
                location=routing_pb2.LocationPointProto(lat=10.0350, lng=105.7800),
                is_completed=True,
            ),
            routing_pb2.RouteStopStateProto(
                id="order-2",
                name="Remaining Stop",
                location=routing_pb2.LocationPointProto(lat=10.0452, lng=105.7469),
                is_completed=False,
            ),
        ],
        new_stop=routing_pb2.StopItemProto(
            id="order-new",
            name="Urgent New Stop",
            location=routing_pb2.LocationPointProto(lat=10.0400, lng=105.7600),
        ),
        max_added_duration_minutes=45.0,
        time_weight=0.5,
        cost_weight=0.5,
    )
    response = stub.OptimizeInsert(request, timeout=10.0)
    assert response.algorithm_used == "ortools"
    assert response.feasible is True
    assert len(response.ordered_stops) == 3
    assert response.ordered_stops[0].stop_id == "order-1"


def test_grpc_optimize_reoptimize(grpc_channel):
    stub = routing_pb2_grpc.RoutingEngineGrpcServiceStub(grpc_channel)
    request = routing_pb2.RouteReoptimizeGrpcRequest(
        driver_location=routing_pb2.LocationPointProto(lat=10.0316, lng=105.7880),
        current_stops=[
            routing_pb2.RouteStopStateProto(
                id="order-far",
                location=routing_pb2.LocationPointProto(lat=10.0900, lng=105.7000),
                is_completed=False,
            ),
            routing_pb2.RouteStopStateProto(
                id="order-near",
                location=routing_pb2.LocationPointProto(lat=10.0320, lng=105.7870),
                is_completed=False,
            ),
        ],
        reason="TRAFFIC_CONGESTION",
        min_improvement_km=0.1,
    )
    response = stub.OptimizeReoptimize(request, timeout=10.0)
    assert response.algorithm_used == "ortools"
    assert len(response.ordered_stops) == 2

