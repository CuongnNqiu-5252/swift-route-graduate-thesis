# service/optimizer_service.py
"""
Orchestrates the route optimisation pipeline:
  1. Build Haversine distance matrix from depot + stop coordinates.
  2. Select algorithm based on request or auto-selection heuristic.
  3. Run the selected TSP solver on the distance matrix.
  4. Map solver output back to OrderedStop objects.
"""
from __future__ import annotations

import logging
import time
import uuid
from datetime import datetime, timezone
from typing import List, Tuple

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
    StopItem,
    WhatIfOptimizationRequest,
    WhatIfOptimizationResponse,
    WhatIfScenario,
)
from core.distance import build_distance_matrix, build_time_matrix, build_multi_objective_matrix
from core.tsp_ortools import ortools_tsp


def solve_batch(request: BatchOptimizationRequest) -> BatchOptimizationResponse:
    """
    Main entry point for batch route optimisation.

    Args:
        request: BatchOptimizationRequest containing depot, stops and preferred algorithm.

    Returns:
        BatchOptimizationResponse with the optimised ordered stop sequence.
    """
    n_stops = len(request.stops)
    logger.info(
        "🚀 [RoutingEngine] Bắt đầu giải bài toán TSP cho %d điểm giao | Kho xuất phát (Depot): (%.4f, %.4f)",
        n_stops, request.depot.lat, request.depot.lng
    )

    # Build location list: index 0 = depot, indices 1..N = delivery stops
    all_locations: List[Tuple[float, float]] = [
        (request.depot.lat, request.depot.lng)
    ] + [
        (stop.location.lat, stop.location.lng) for stop in request.stops
    ]

    n_total = len(all_locations)  # depot + stops

    # Build distance, time, and multi-objective cost matrices
    dist_matrix = build_distance_matrix(all_locations)
    time_matrix = build_time_matrix(dist_matrix, avg_speed_kmh=28.0)
    time_w = request.time_weight if request.time_weight is not None else 0.5
    cost_w = request.cost_weight if request.cost_weight is not None else 0.5
    cost_matrix = build_multi_objective_matrix(dist_matrix, time_matrix, time_w, cost_w)
    logger.info("📐 [RoutingEngine] Đã xây dựng ma trận chi phí đa mục tiêu %dx%d (W_time=%.2f, W_cost=%.2f)", n_total, n_total, time_w, cost_w)

    # Algorithm execution: Google OR-Tools is the sole production routing solver
    algorithm_used = "ortools"
    logger.info("🧠 [RoutingEngine] Giải bài toán lộ trình bằng Google OR-Tools")

    # Run solver on multi-objective cost matrix and measure wall-clock CPU time
    start_ns = time.perf_counter_ns()
    route, _ = ortools_tsp(cost_matrix, start=0)

    # Evaluate exact ground-truth distance using dist_matrix
    total_dist = sum(
        dist_matrix[route[i]][route[i + 1]]
        for i in range(len(route) - 1)
    ) if len(route) > 1 else 0.0

    elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0

    # Calculate total duration from time matrix
    total_minutes = sum(
        time_matrix[route[i]][route[i + 1]]
        for i in range(len(route) - 1)
    ) if len(route) > 1 else 0.0

    logger.info(
        "✅ [RoutingEngine] Hoàn thành giải TSP bằng '%s' trong %.2f ms | Tổng quãng đường: %.3f km | Thời gian dự kiến: %.1f phút",
        algorithm_used, elapsed_ms, total_dist, total_minutes
    )

    # Map route (list of matrix indices) back to OrderedStop objects.
    # Skip index 0 (depot) at start and end of route.
    ordered_stops: List[OrderedStop] = []
    cumulative_dist = 0.0
    cumulative_time = 0.0
    prev_idx = 0  # depot

    for position, matrix_idx in enumerate(route[1:], start=1):
        if matrix_idx == 0:
            # Skip the return leg to depot
            break

        cumulative_dist += dist_matrix[prev_idx][matrix_idx]
        cumulative_time += time_matrix[prev_idx][matrix_idx]

        stop_data = request.stops[matrix_idx - 1]  # stops list is 0-indexed
        ordered_stops.append(OrderedStop(
            stop_index=position,
            stop_id=stop_data.id,
            location=stop_data.location,
            cumulative_distance_km=round(cumulative_dist, 3),
            estimated_arrival_minutes=round(cumulative_time, 1),
        ))
        prev_idx = matrix_idx

    return BatchOptimizationResponse(
        job_id=str(uuid.uuid4()),
        algorithm_used=algorithm_used,
        total_distance_km=round(total_dist, 3),
        total_duration_minutes=round(total_minutes, 1),
        ordered_stops=ordered_stops,
        execution_time_ms=round(elapsed_ms, 2),
        timestamp=datetime.now(timezone.utc).isoformat(),
    )


def _simulate_driver_capacity(
    request: WhatIfOptimizationRequest,
    driver_count: int,
) -> WhatIfScenario:
    """Return a deterministic in-memory multi-driver estimate for a What-If run.

    Stops are first put in a globally sensible order, then split into contiguous
    route segments. Each segment is solved independently from the depot. This is
    deliberately a dry-run: it creates no plans and changes no order state.
    """
    global_route = solve_batch(BatchOptimizationRequest(
        depot=request.depot,
        stops=request.stops,
        algorithm=request.algorithm,
    ))
    stop_by_id = {stop.id: stop for stop in request.stops}
    ordered = [stop_by_id[stop.stop_id] for stop in global_route.ordered_stops]
    active_drivers = min(driver_count, len(ordered))
    chunk_size = (len(ordered) + active_drivers - 1) // active_drivers
    routes = [ordered[index:index + chunk_size] for index in range(0, len(ordered), chunk_size)]
    results = [solve_batch(BatchOptimizationRequest(
        depot=request.depot,
        stops=route,
        algorithm=request.algorithm,
    )) for route in routes]
    completion_minutes = max(result.total_duration_minutes for result in results)
    late_risk_orders = sum(
        len(result.ordered_stops)
        for result in results
        if result.total_duration_minutes > request.max_shift_minutes
    )
    return WhatIfScenario(
        driver_count=active_drivers,
        total_distance_km=round(sum(result.total_distance_km for result in results), 3),
        completion_minutes=round(completion_minutes, 1),
        late_risk_orders=late_risk_orders,
        algorithm_used=global_route.algorithm_used,
    )


def simulate_what_if(request: WhatIfOptimizationRequest) -> WhatIfOptimizationResponse:
    """Compare two staffing levels without writing to SQLite or PostgreSQL."""
    baseline = _simulate_driver_capacity(request, request.baseline_driver_count)
    scenario = _simulate_driver_capacity(request, request.scenario_driver_count)

    def percent_change(current: float, reference: float) -> float:
        return round(((current - reference) / reference) * 100, 1) if reference else 0.0

    return WhatIfOptimizationResponse(
        baseline=baseline,
        scenario=scenario,
        distance_delta_percent=percent_change(scenario.total_distance_km, baseline.total_distance_km),
        completion_delta_percent=percent_change(scenario.completion_minutes, baseline.completion_minutes),
        late_risk_delta=scenario.late_risk_orders - baseline.late_risk_orders,
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

    if len(remaining_states) <= 1:
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
    seq_coords: List[Tuple[float, float]] = [
        (request.driver_location.lat, request.driver_location.lng)
    ] + [(s.location.lat, s.location.lng) for s in remaining_states]
    seq_dist_matrix = build_distance_matrix(seq_coords)
    seq_time_matrix = build_time_matrix(seq_dist_matrix, avg_speed_kmh=28.0)
    current_remaining_dist = sum(
        seq_dist_matrix[i][i + 1] for i in range(len(seq_coords) - 1)
    )

    time_w = request.time_weight if request.time_weight is not None else 0.5
    cost_w = request.cost_weight if request.cost_weight is not None else 0.5
    cost_matrix = build_multi_objective_matrix(seq_dist_matrix, seq_time_matrix, time_w, cost_w)
    # Open-path formulation from driver's current GPS position: zero cost to return to node 0
    for row in cost_matrix:
        row[0] = 0.0

    route, _ = ortools_tsp(cost_matrix, start=0)

    reopt_stops: List[OrderedStop] = []
    cum_dist = 0.0
    cum_time = 0.0
    prev_idx = 0
    for pos, matrix_idx in enumerate(route[1:], start=1):
        if matrix_idx == 0:
            break
        cum_dist += seq_dist_matrix[prev_idx][matrix_idx]
        cum_time += seq_time_matrix[prev_idx][matrix_idx]
        st = remaining_states[matrix_idx - 1]
        reopt_stops.append(
            OrderedStop(
                stop_index=pos,
                stop_id=st.id,
                location=st.location,
                cumulative_distance_km=round(cum_dist, 3),
                estimated_arrival_minutes=round(cum_time, 1),
            )
        )
        prev_idx = matrix_idx

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
        for idx, s in enumerate(remaining_states, start=1):
            combined_stops.append(
                OrderedStop(
                    stop_index=offset + idx,
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
        stops_changed=stops_changed,
        distance_saved_km=distance_saved if stops_changed else 0.0,
        total_distance_km=round(cum_dist, 3) if stops_changed else round(current_remaining_dist, 3),
        total_duration_minutes=round(cum_time, 1),
        ordered_stops=combined_stops,
        execution_time_ms=round(elapsed_ms, 2),
        timestamp=datetime.now(timezone.utc).isoformat(),
    )

