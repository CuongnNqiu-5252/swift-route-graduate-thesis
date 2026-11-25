# service/grpc_server.py
"""
gRPC Server implementation for the SwiftRoute Routing Engine.
Exposes OptimizeBatch, OptimizeInsert, and OptimizeReoptimize
over binary Protocol Buffers on port 50051 alongside the FastAPI server.
"""
from __future__ import annotations

from concurrent import futures
import logging
import os
from typing import Optional

import grpc
from core.road_matrix import RoadMatrixError

from api.grpc_gen import routing_pb2, routing_pb2_grpc
from api.schemas import (
    BatchOptimizationRequest,
    LocationPoint,
    OrderedStop,
    RouteInsertRequest,
    RouteReoptimizeRequest,
    RouteStopState,
    StopItem,
)
from service.optimizer_service import (
    solve_batch,
    solve_insert,
    solve_reoptimize,
)

logger = logging.getLogger("uvicorn.error")


def _to_location_schema(proto_loc: routing_pb2.LocationPointProto) -> LocationPoint:
    return LocationPoint(
        lat=proto_loc.lat,
        lng=proto_loc.lng,
        address=proto_loc.address if proto_loc.address else None,
    )


def _to_location_proto(loc: LocationPoint) -> routing_pb2.LocationPointProto:
    return routing_pb2.LocationPointProto(
        lat=loc.lat,
        lng=loc.lng,
        address=loc.address or "",
    )


def _to_stop_item_schema(proto_stop: routing_pb2.StopItemProto) -> StopItem:
    return StopItem(
        id=proto_stop.id,
        name=proto_stop.name if proto_stop.name else None,
        location=_to_location_schema(proto_stop.location),
        demand_kg=proto_stop.demand_kg,
        service_time=proto_stop.service_time if proto_stop.HasField("service_time") else 5.0,
        time_window_start=proto_stop.time_window_start if proto_stop.time_window_start else None,
        time_window_end=proto_stop.time_window_end if proto_stop.time_window_end else None,
    )


def _to_route_stop_state_schema(proto_state: routing_pb2.RouteStopStateProto) -> RouteStopState:
    return RouteStopState(
        id=proto_state.id,
        name=proto_state.name if proto_state.name else None,
        location=_to_location_schema(proto_state.location),
        demand_kg=proto_state.demand_kg,
        service_time=proto_state.service_time if proto_state.HasField("service_time") else 5.0,
        time_window_start=proto_state.time_window_start if proto_state.time_window_start else None,
        time_window_end=proto_state.time_window_end if proto_state.time_window_end else None,
        is_completed=proto_state.is_completed,
    )


def _to_ordered_stop_proto(stop: OrderedStop) -> routing_pb2.OrderedStopProto:
    return routing_pb2.OrderedStopProto(
        stop_index=stop.stop_index,
        stop_id=stop.stop_id,
        location=_to_location_proto(stop.location),
        cumulative_distance_km=stop.cumulative_distance_km,
        estimated_arrival_minutes=stop.estimated_arrival_minutes,
        late_by_minutes=stop.late_by_minutes,
    )


def _verify_internal_auth(context: grpc.ServicerContext) -> bool:
    """Checks x-internal-key metadata when TENANT_INTERNAL_KEY is configured."""
    expected_key = os.getenv("TENANT_INTERNAL_KEY", "").strip()
    metadata = dict(context.invocation_metadata())
    provided_key = metadata.get("x-internal-key")
    if provided_key is not None:
        if not expected_key or provided_key != expected_key:
            context.abort(grpc.StatusCode.UNAUTHENTICATED, "Invalid internal key")
            return False
        return True
    if expected_key:
        context.abort(grpc.StatusCode.UNAUTHENTICATED, "Internal key required")
        return False
    return True


def _solve_with_road_errors(solver, request, context):
    try:
        return solver(request)
    except RoadMatrixError as exc:
        context.abort(grpc.StatusCode.UNAVAILABLE, str(exc))


class RoutingEngineServicer(routing_pb2_grpc.RoutingEngineGrpcServiceServicer):
    """Implements the RoutingEngineGrpcService protobuf contract."""

    def OptimizeBatch(
        self,
        request: routing_pb2.BatchOptimizationGrpcRequest,
        context: grpc.ServicerContext,
    ) -> routing_pb2.BatchOptimizationGrpcResponse:
        _verify_internal_auth(context)
        if not request.stops:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "stops must not be empty")

        logger.info(
            "⚡ [gRPC Server] OptimizeBatch received: %d stops (tenant=%s)",
            len(request.stops),
            request.tenant_id or "system",
        )
        schema_req = BatchOptimizationRequest(
            depot=_to_location_schema(request.depot) if request.HasField("depot") else None,
            stops=[_to_stop_item_schema(s) for s in request.stops],
            algorithm=request.algorithm if request.algorithm else "auto",
            time_weight=request.time_weight if request.HasField("time_weight") else 0.5,
            cost_weight=request.cost_weight if request.HasField("cost_weight") else 0.5,
            vehicle_count=request.vehicle_count or 1,
            max_stops_per_vehicle=request.max_stops_per_vehicle or None,
            planned_departure_at=request.planned_departure_at or None,
        )
        res = _solve_with_road_errors(solve_batch, schema_req, context)
        return routing_pb2.BatchOptimizationGrpcResponse(
            job_id=res.job_id,
            total_distance_km=res.total_distance_km,
            total_duration_minutes=res.total_duration_minutes,
            ordered_stops=[_to_ordered_stop_proto(s) for s in res.ordered_stops],
            execution_time_ms=res.execution_time_ms,
            timestamp=res.timestamp,
            vehicle_routes=[
                routing_pb2.VehicleRouteProto(
                    vehicle_index=route.vehicle_index,
                    total_distance_km=route.total_distance_km,
                    total_duration_minutes=route.total_duration_minutes,
                    total_load_kg=route.total_load_kg,
                    ordered_stops=[_to_ordered_stop_proto(s) for s in route.ordered_stops],
                )
                for route in res.vehicle_routes
            ],
        )

    def OptimizeInsert(
        self,
        request: routing_pb2.RouteInsertGrpcRequest,
        context: grpc.ServicerContext,
    ) -> routing_pb2.RouteInsertGrpcResponse:
        _verify_internal_auth(context)
        logger.info(
            "⚡ [gRPC Server] OptimizeInsert received: %d current stops + new stop %s",
            len(request.current_stops),
            request.new_stop.id,
        )
        schema_req = RouteInsertRequest(
            driver_location=_to_location_schema(request.driver_location),
            current_stops=[_to_route_stop_state_schema(s) for s in request.current_stops],
            new_stop=_to_stop_item_schema(request.new_stop),
            max_added_duration_minutes=(
                request.max_added_duration_minutes
                if request.max_added_duration_minutes > 0
                else 30.0
            ),
            time_weight=request.time_weight,
            cost_weight=request.cost_weight,
        )
        res = _solve_with_road_errors(solve_insert, schema_req, context)
        return routing_pb2.RouteInsertGrpcResponse(
            job_id=res.job_id,
            feasible=res.feasible,
            inserted_stop_index=res.inserted_stop_index,
            added_distance_km=res.added_distance_km,
            added_duration_minutes=res.added_duration_minutes,
            total_distance_km=res.total_distance_km,
            total_duration_minutes=res.total_duration_minutes,
            ordered_stops=[_to_ordered_stop_proto(s) for s in res.ordered_stops],
            execution_time_ms=res.execution_time_ms,
            timestamp=res.timestamp,
        )

    def OptimizeReoptimize(
        self,
        request: routing_pb2.RouteReoptimizeGrpcRequest,
        context: grpc.ServicerContext,
    ) -> routing_pb2.RouteReoptimizeGrpcResponse:
        _verify_internal_auth(context)
        if not request.current_stops:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "current_stops must not be empty")

        logger.info(
            "⚡ [gRPC Server] OptimizeReoptimize received: %d stops (reason=%s)",
            len(request.current_stops),
            request.reason or "DELAY_DETECTED",
        )
        schema_req = RouteReoptimizeRequest(
            driver_location=_to_location_schema(request.driver_location),
            current_stops=[_to_route_stop_state_schema(s) for s in request.current_stops],
            reason=request.reason if request.reason else "DELAY_DETECTED",
            min_improvement_km=(
                request.min_improvement_km if request.min_improvement_km >= 0 else 0.2
            ),
            time_weight=request.time_weight if request.time_weight > 0 else 0.5,
            cost_weight=request.cost_weight if request.cost_weight > 0 else 0.5,
        )
        res = _solve_with_road_errors(solve_reoptimize, schema_req, context)
        return routing_pb2.RouteReoptimizeGrpcResponse(
            job_id=res.job_id,
            stops_changed=res.stops_changed,
            distance_saved_km=res.distance_saved_km,
            total_distance_km=res.total_distance_km,
            total_duration_minutes=res.total_duration_minutes,
            ordered_stops=[_to_ordered_stop_proto(s) for s in res.ordered_stops],
            execution_time_ms=res.execution_time_ms,
            timestamp=res.timestamp,
        )

_grpc_server_instance: Optional[grpc.Server] = None


def start_grpc_server(port: Optional[int] = None) -> grpc.Server:
    """Starts the gRPC server in a background thread pool on the configured port."""
    global _grpc_server_instance
    if _grpc_server_instance is not None:
        return _grpc_server_instance

    actual_port = port if port is not None else int(os.getenv("GRPC_PORT", "50051"))
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    routing_pb2_grpc.add_RoutingEngineGrpcServiceServicer_to_server(
        RoutingEngineServicer(), server
    )
    server.add_insecure_port(f"[::]:{actual_port}")
    server.start()
    _grpc_server_instance = server
    logger.info("⚡ [gRPC Server] RoutingEngineGrpcService listening on 0.0.0.0:%d", actual_port)
    return server


def stop_grpc_server(grace: Optional[float] = 2.0) -> None:
    """Stops the background gRPC server cleanly."""
    global _grpc_server_instance
    if _grpc_server_instance is not None:
        _grpc_server_instance.stop(grace)
        _grpc_server_instance = None
        logger.info("🛑 [gRPC Server] RoutingEngineGrpcService stopped")

