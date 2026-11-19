# service/optimizer_service.py
"""
Orchestrates the route optimisation pipeline:
  1. Build Goong road distance matrix from depot + stop coordinates.
  2. Select algorithm based on request or auto-selection heuristic.
  3. Run the selected TSP solver on the distance matrix.
  4. Map solver output back to OrderedStop objects.
"""
from __future__ import annotations

import logging
import math
import time
import uuid
from datetime import datetime, timezone
from typing import List, Optional, Sequence, Tuple, Union

# Use uvicorn logger so logs stream directly to docker container stdout
logger = logging.getLogger("uvicorn.error")

from api.schemas import (
    BatchOptimizationRequest,
    BatchOptimizationResponse,
    LocationPoint,
    OrderedStop,
    RouteInsertRequest,
    RouteInsertResponse,
    RouteReoptimizeRequest,
    RouteReoptimizeResponse,
    RouteStopState,
    StopItem,
    VehicleRoute,
)
from core.distance import build_multi_objective_matrix
from core.road_matrix import build_open_road_matrices, build_road_matrices
from core.tsp_ortools import ortools_tsp, ortools_vrp


def _trace_open_route(
    route: List[int],
    stops: Sequence[Union[StopItem, RouteStopState]],
    dist_matrix: List[List[float]],
    time_matrix: List[List[float]],
    windows: List[Tuple[int, int] | None] | None = None,
) -> Tuple[List[OrderedStop], float, float, float]:
    """Accumulate open-path OrderedStops, total distance (km), total duration (min), and load (kg)."""
    ordered: List[OrderedStop] = []
    cum_dist = 0.0
    cum_time = 0.0
    elapsed_seconds = 0
    total_load = 0.0
    prev_idx = 0

    for position, matrix_idx in enumerate(route[1:], start=1):
        if matrix_idx == 0:
            break
        cum_dist += dist_matrix[prev_idx][matrix_idx]
        late_minutes = 0
        if windows is None:
            cum_time += time_matrix[prev_idx][matrix_idx]
        else:
            elapsed_seconds += math.ceil(time_matrix[prev_idx][matrix_idx] * 60)
            window = windows[matrix_idx]
            if window is not None:
                elapsed_seconds = max(elapsed_seconds, window[0])
                late_minutes = max(0, math.ceil((elapsed_seconds - window[1]) / 60))
            cum_time = elapsed_seconds / 60
        stop_data = stops[matrix_idx - 1]
        total_load += max(0.0, stop_data.demand_kg)
        ordered.append(
            OrderedStop(
                stop_index=position,
                stop_id=stop_data.id,
                location=stop_data.location,
                cumulative_distance_km=round(cum_dist, 3),
                estimated_arrival_minutes=round(cum_time, 1),
                late_by_minutes=late_minutes,
            )
        )
        if windows is None:
            cum_time += stop_data.service_time
        else:
            elapsed_seconds += math.ceil(stop_data.service_time * 60)
            cum_time = elapsed_seconds / 60
        prev_idx = matrix_idx

    return ordered, cum_dist, cum_time, total_load


def _delivery_windows(request: BatchOptimizationRequest) -> List[Tuple[int, int] | None] | None:
    departure = request.planned_departure_at
    if departure is None:
        return None
    windows: List[Tuple[int, int] | None] = [None]
    for stop in request.stops:
        if not stop.time_window_start and not stop.time_window_end:
            windows.append(None)
            continue
        if not stop.time_window_start or not stop.time_window_end:
            raise ValueError(f"Incomplete appointment window for stop {stop.id}")
        start = datetime.fromisoformat(stop.time_window_start.replace("Z", "+00:00"))
        end = datetime.fromisoformat(stop.time_window_end.replace("Z", "+00:00"))
        if start.tzinfo is None or end.tzinfo is None or end <= start:
            raise ValueError(f"Invalid appointment window for stop {stop.id}")
        windows.append((
            max(0, math.ceil((start - departure).total_seconds())),
            math.floor((end - departure).total_seconds()),
        ))
    return windows if any(window is not None for window in windows) else None


def _build_batch_matrices(
    depot: Optional[LocationPoint],
    stops: Sequence[StopItem],
) -> Tuple[List[List[float]], List[List[float]], str]:
    """Build (N+1)x(N+1) open-path matrices with real depot or zero-cost virtual depot at index 0."""
    stop_locations = [(stop.location.lat, stop.location.lng) for stop in stops]
    if depot is not None:
        return build_open_road_matrices((depot.lat, depot.lng), stop_locations)

    stop_dist, stop_time, distance_source = build_road_matrices(stop_locations)
    n = len(stops) + 1
    dist_matrix = [[0.0] * n] + [[0.0] + row for row in stop_dist]
    time_matrix = [[0.0] * n] + [[0.0] + row for row in stop_time]
    return dist_matrix, time_matrix, distance_source


def measure_ordered_route(request: BatchOptimizationRequest) -> BatchOptimizationResponse:
    """Measure the open route in input order, without invoking a solver."""
    start_ns = time.perf_counter_ns()
    distances, durations, _ = _build_batch_matrices(request.depot, request.stops)
    stops, distance, duration, _ = _trace_open_route(
        list(range(len(request.stops) + 1)), request.stops, distances, durations, _delivery_windows(request)
    )
    return BatchOptimizationResponse(
        job_id=str(uuid.uuid4()),
        algorithm_used="manual",
        total_distance_km=round(distance, 3),
        total_duration_minutes=round(duration, 1),
        ordered_stops=stops,
        execution_time_ms=(time.perf_counter_ns() - start_ns) / 1_000_000.0,
        timestamp=datetime.now(timezone.utc).isoformat(),
    )


def solve_batch(request: BatchOptimizationRequest) -> BatchOptimizationResponse:
    """
    Main entry point for batch route optimisation (single-vehicle TSP or multi-vehicle CVRP).

    Args:
        request: BatchOptimizationRequest containing depot, stops, preferred algorithm, and vehicle_count.

    Returns:
        BatchOptimizationResponse with the optimised ordered stop sequence and per-vehicle routes.
    """
    n_stops = len(request.stops)
    requested_vehicles = max(1, request.vehicle_count)
    effective_vehicles = min(requested_vehicles, n_stops) if n_stops > 0 else 1

    depot_label = (
        f"({request.depot.lat:.4f}, {request.depot.lng:.4f})"
        if request.depot is not None
        else "virtual (free-start)"
    )
    logger.info(
        "🚀 [RoutingEngine] Bắt đầu giải bài toán định tuyến cho %d điểm giao với %d xe | Kho xuất phát (Depot): %s",
        n_stops, effective_vehicles, depot_label
    )

    # Build (N+1) x (N+1) distance, time, and multi-objective cost matrices (index 0 = depot or virtual depot)
    dist_matrix, time_matrix, distance_source = _build_batch_matrices(request.depot, request.stops)
    n_total = len(dist_matrix)
    time_w = request.time_weight if request.time_weight is not None else 0.5
    cost_w = request.cost_weight if request.cost_weight is not None else 0.5
    cost_matrix = build_multi_objective_matrix(dist_matrix, time_matrix, time_w, cost_w)
    logger.info("📐 [RoutingEngine] Đã xây dựng ma trận chi phí đa mục tiêu %dx%d (%s, W_time=%.2f, W_cost=%.2f)", n_total, n_total, distance_source, time_w, cost_w)

    algorithm_used = "ortools"
    windows = _delivery_windows(request)
    start_ns = time.perf_counter_ns()

    if effective_vehicles > 1 or (windows is not None and n_stops > 1):
        logger.info("🧠 [RoutingEngine] Giải bài toán CVRP nhiều tài xế (%d xe) bằng Google OR-Tools", effective_vehicles)
        demands = [0.0] + [max(0.0, s.demand_kg) for s in request.stops]
        total_demand = sum(demands)
        default_cap = max(100.0, total_demand + 10.0)
        if request.vehicle_capacities and len(request.vehicle_capacities) >= effective_vehicles:
            capacities = list(request.vehicle_capacities[:effective_vehicles])
            if sum(capacities) < total_demand:
                capacities = [default_cap] * effective_vehicles
        else:
            capacities = [default_cap] * effective_vehicles

        vrp_solution = ortools_vrp(
            cost_matrix=cost_matrix,
            demands=demands,
            capacities=capacities,
            num_vehicles=effective_vehicles,
            start=0,
            max_stops_per_vehicle=request.max_stops_per_vehicle,
            time_matrix=time_matrix if windows is not None else None,
            delivery_windows=windows,
            service_times=[0.0] + [stop.service_time for stop in request.stops],
        )
        elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0

        vehicle_routes: List[VehicleRoute] = []
        all_ordered_stops: List[OrderedStop] = []
        fleet_dist = 0.0
        fleet_minutes = 0.0
        global_pos = 1

        for v_idx in range(effective_vehicles):
            v_route = vrp_solution["routes"].get(v_idx, [])
            v_stops, v_total_dist, v_total_min, v_load = _trace_open_route(
                v_route, request.stops, dist_matrix, time_matrix, windows
            )
            for stop in v_stops:
                all_ordered_stops.append(OrderedStop(
                    stop_index=global_pos,
                    stop_id=stop.stop_id,
                    location=stop.location,
                    cumulative_distance_km=stop.cumulative_distance_km,
                    estimated_arrival_minutes=stop.estimated_arrival_minutes,
                    late_by_minutes=stop.late_by_minutes,
                ))
                global_pos += 1

            if v_stops:
                fleet_dist += v_total_dist
                fleet_minutes += v_total_min
                vehicle_routes.append(VehicleRoute(
                    vehicle_index=len(vehicle_routes),
                    total_distance_km=round(v_total_dist, 3),
                    total_duration_minutes=round(v_total_min, 1),
                    total_load_kg=round(v_load, 2),
                    ordered_stops=v_stops,
                ))

        logger.info(
            "✅ [RoutingEngine] Hoàn thành giải CVRP (%d xe) bằng '%s' trong %.2f ms | Tổng quãng đường đội xe: %.3f km",
            len(vehicle_routes), algorithm_used, elapsed_ms, fleet_dist
        )

        return BatchOptimizationResponse(
            job_id=str(uuid.uuid4()),
            algorithm_used=algorithm_used,
            total_distance_km=round(fleet_dist, 3),
            total_duration_minutes=round(fleet_minutes, 1),
            ordered_stops=all_ordered_stops,
            vehicle_routes=vehicle_routes,
            execution_time_ms=round(elapsed_ms, 2),
            timestamp=datetime.now(timezone.utc).isoformat(),
        )

    logger.info("🧠 [RoutingEngine] Giải bài toán lộ trình bằng Google OR-Tools")

    # Run solver on open-path multi-objective cost matrix and measure wall-clock CPU time
    route, _ = ortools_tsp(cost_matrix, start=0)
    ordered_stops, total_dist, total_minutes, total_load_kg = _trace_open_route(
        route, request.stops, dist_matrix, time_matrix, windows
    )
    elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0

    logger.info(
        "✅ [RoutingEngine] Hoàn thành giải TSP bằng '%s' trong %.2f ms | Tổng quãng đường: %.3f km | Thời gian dự kiến: %.1f phút",
        algorithm_used, elapsed_ms, total_dist, total_minutes
    )

    total_load_kg = round(total_load_kg, 2)
    single_vehicle_route = [
        VehicleRoute(
            vehicle_index=0,
            total_distance_km=round(total_dist, 3),
            total_duration_minutes=round(total_minutes, 1),
            total_load_kg=total_load_kg,
            ordered_stops=ordered_stops,
        )
    ] if ordered_stops else []

    return BatchOptimizationResponse(
        job_id=str(uuid.uuid4()),
        algorithm_used=algorithm_used,
        total_distance_km=round(total_dist, 3),
        total_duration_minutes=round(total_minutes, 1),
        ordered_stops=ordered_stops,
        vehicle_routes=single_vehicle_route,
        execution_time_ms=round(elapsed_ms, 2),
        timestamp=datetime.now(timezone.utc).isoformat(),
    )


def solve_insert(request: RouteInsertRequest) -> RouteInsertResponse:
    """
    Dynamically inserts a new stop into an ongoing route while preserving all
    completed stops in their exact sequence (P3.1).
    """
    start_ns = time.perf_counter_ns()
    completed = [s for s in request.current_stops if s.is_completed]
    remaining = [
        StopItem(
            id=s.id,
            name=s.name,
            location=s.location,
            demand_kg=s.demand_kg,
            service_time=s.service_time,
            time_window_start=s.time_window_start,
            time_window_end=s.time_window_end,
        )
        for s in request.current_stops
        if not s.is_completed
    ]

    # Baseline cost of remaining stops without the new stop
    baseline_dist = 0.0
    baseline_dur = 0.0
    if remaining:
        base_res = solve_batch(
            BatchOptimizationRequest(
                depot=request.driver_location,
                stops=remaining,
                algorithm="ortools",
                time_weight=request.time_weight,
                cost_weight=request.cost_weight,
            )
        )
        baseline_dist = base_res.total_distance_km
        baseline_dur = base_res.total_duration_minutes

    # Solve remaining stops + new_stop starting from driver's current GPS position
    candidate_stops = remaining + [request.new_stop]
    candidate_res = solve_batch(
        BatchOptimizationRequest(
            depot=request.driver_location,
            stops=candidate_stops,
            algorithm="ortools",
            time_weight=request.time_weight,
            cost_weight=request.cost_weight,
        )
    )

    added_dist = round(max(0.0, candidate_res.total_distance_km - baseline_dist), 3)
    added_dur = round(max(0.0, candidate_res.total_duration_minutes - baseline_dur), 1)
    feasible = added_dur <= request.max_added_duration_minutes

    combined_stops: List[OrderedStop] = []
    for idx, s in enumerate(completed, start=1):
        combined_stops.append(
            OrderedStop(
                stop_index=idx,
                stop_id=s.id,
                location=s.location,
                cumulative_distance_km=0.0,
                estimated_arrival_minutes=0.0,
            )
        )

    inserted_index = len(completed) + 1
    offset = len(completed)
    for stop in candidate_res.ordered_stops:
        global_idx = offset + stop.stop_index
        if stop.stop_id == request.new_stop.id:
            inserted_index = global_idx
        combined_stops.append(
            OrderedStop(
                stop_index=global_idx,
                stop_id=stop.stop_id,
                location=stop.location,
                cumulative_distance_km=stop.cumulative_distance_km,
                estimated_arrival_minutes=stop.estimated_arrival_minutes,
            )
        )

    elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0
    return RouteInsertResponse(
        job_id=str(uuid.uuid4()),
        algorithm_used=candidate_res.algorithm_used,
        feasible=feasible,
        inserted_stop_index=inserted_index,
        added_distance_km=added_dist,
        added_duration_minutes=added_dur,
        total_distance_km=candidate_res.total_distance_km,
        total_duration_minutes=candidate_res.total_duration_minutes,
        ordered_stops=combined_stops,
        execution_time_ms=round(elapsed_ms, 2),
        timestamp=datetime.now(timezone.utc).isoformat(),
    )


def solve_reoptimize(request: RouteReoptimizeRequest) -> RouteReoptimizeResponse:
    """
    Safely reoptimizes only the uncompleted stops of an active route from the
    driver's current GPS location while preserving completed stops (P3.2).
    """
    start_ns = time.perf_counter_ns()
    completed = [s for s in request.current_stops if s.is_completed]
    remaining_states = [s for s in request.current_stops if not s.is_completed]

    if not remaining_states:
        combined: List[OrderedStop] = []
        for idx, s in enumerate(completed + remaining_states, start=1):
            combined.append(
                OrderedStop(
                    stop_index=idx,
                    stop_id=s.id,
                    location=s.location,
                    cumulative_distance_km=0.0,
                    estimated_arrival_minutes=0.0,
                )
            )
        elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0
        return RouteReoptimizeResponse(
            job_id=str(uuid.uuid4()),
            algorithm_used="ortools",
            stops_changed=False,
            distance_saved_km=0.0,
            total_distance_km=0.0,
            total_duration_minutes=0.0,
            ordered_stops=combined,
            execution_time_ms=round(elapsed_ms, 2),
            timestamp=datetime.now(timezone.utc).isoformat(),
        )

    # Calculate current open-path sequence distance from driver_location through remaining_states
    stop_coords = [(s.location.lat, s.location.lng) for s in remaining_states]
    seq_dist_matrix, seq_time_matrix, _ = build_open_road_matrices(
        (request.driver_location.lat, request.driver_location.lng), stop_coords
    )
    current_remaining_dist = sum(
        seq_dist_matrix[i][i + 1] for i in range(len(remaining_states))
    )

    time_w = request.time_weight if request.time_weight is not None else 0.5
    cost_w = request.cost_weight if request.cost_weight is not None else 0.5
    cost_matrix = build_multi_objective_matrix(seq_dist_matrix, seq_time_matrix, time_w, cost_w)

    route, _ = ortools_tsp(cost_matrix, start=0)
    reopt_stops, cum_dist, cum_time, _ = _trace_open_route(
        route, remaining_states, seq_dist_matrix, seq_time_matrix
    )

    old_ids = [s.id for s in remaining_states]
    new_ids = [s.stop_id for s in reopt_stops]
    distance_saved = round(max(0.0, current_remaining_dist - cum_dist), 3)
    stops_changed = (old_ids != new_ids) and (distance_saved >= request.min_improvement_km)

    combined_stops: List[OrderedStop] = []
    for idx, s in enumerate(completed, start=1):
        combined_stops.append(
            OrderedStop(
                stop_index=idx,
                stop_id=s.id,
                location=s.location,
                cumulative_distance_km=0.0,
                estimated_arrival_minutes=0.0,
            )
        )

    offset = len(completed)
    if stops_changed:
        for stop in reopt_stops:
            combined_stops.append(
                OrderedStop(
                    stop_index=offset + stop.stop_index,
                    stop_id=stop.stop_id,
                    location=stop.location,
                    cumulative_distance_km=stop.cumulative_distance_km,
                    estimated_arrival_minutes=stop.estimated_arrival_minutes,
                )
            )
    else:
        original_stops, _, cum_time, _ = _trace_open_route(
            list(range(len(remaining_states) + 1)), remaining_states, seq_dist_matrix, seq_time_matrix
        )
        for idx, s in enumerate(original_stops, start=1):
            combined_stops.append(
                OrderedStop(
                    stop_index=offset + idx,
                    stop_id=s.stop_id,
                    location=s.location,
                    cumulative_distance_km=s.cumulative_distance_km,
                    estimated_arrival_minutes=s.estimated_arrival_minutes,
                )
            )

    elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0
    return RouteReoptimizeResponse(
        job_id=str(uuid.uuid4()),
        algorithm_used="ortools",
        stops_changed=stops_changed,
        distance_saved_km=distance_saved if stops_changed else 0.0,
        total_distance_km=round(cum_dist, 3) if stops_changed else round(current_remaining_dist, 3),
        total_duration_minutes=round(cum_time, 1),
        ordered_stops=combined_stops,
        execution_time_ms=round(elapsed_ms, 2),
        timestamp=datetime.now(timezone.utc).isoformat(),
    )

