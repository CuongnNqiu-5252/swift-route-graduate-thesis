# service/optimizer_service.py
"""
Orchestrates the route optimisation pipeline:
  1. Build Haversine distance matrix from depot + stop coordinates.
  2. Select algorithm based on request or auto-selection heuristic.
  3. Run the selected TSP solver on the distance matrix.
  4. Map solver output back to OrderedStop objects.
"""
from __future__ import annotations

import time
import uuid
from datetime import datetime, timezone
from typing import List, Tuple

from api.schemas import (
    BatchOptimizationRequest,
    BatchOptimizationResponse,
    LocationPoint,
    OrderedStop,
    WhatIfOptimizationRequest,
    WhatIfOptimizationResponse,
    WhatIfScenario,
)
from core.distance import build_distance_matrix, build_time_matrix
from core.tsp_held_karp import held_karp_tsp
from core.tsp_nearest_neighbor import nearest_neighbor_tsp
from core.tsp_genetic import genetic_tsp


# Auto-select threshold: use Held-Karp exact solver for n <= this value.
# Beyond this, exponential memory requirements make it impractical.
HELD_KARP_MAX_N = 14


def solve_batch(request: BatchOptimizationRequest) -> BatchOptimizationResponse:
    """
    Main entry point for batch route optimisation.

    Args:
        request: BatchOptimizationRequest containing depot, stops and preferred algorithm.

    Returns:
        BatchOptimizationResponse with the optimised ordered stop sequence.
    """
    # Build location list: index 0 = depot, indices 1..N = delivery stops
    all_locations: List[Tuple[float, float]] = [
        (request.depot.lat, request.depot.lng)
    ] + [
        (stop.location.lat, stop.location.lng) for stop in request.stops
    ]

    n_total = len(all_locations)  # depot + stops

    # Build distance and time matrices
    dist_matrix = build_distance_matrix(all_locations)
    time_matrix = build_time_matrix(dist_matrix, avg_speed_kmh=28.0)

    # Algorithm selection
    n_stops = len(request.stops)
    algorithm = request.algorithm
    if algorithm == "auto":
        algorithm = "held_karp" if n_stops <= HELD_KARP_MAX_N else "nearest_neighbor"

    # Run solver and measure wall-clock CPU time
    start_ns = time.perf_counter_ns()

    if algorithm == "held_karp":
        route, total_dist = held_karp_tsp(dist_matrix, start=0)
        algorithm_used = "held_karp"
    elif algorithm == "genetic":
        route, total_dist = genetic_tsp(dist_matrix)
        algorithm_used = "genetic"
    else:
        route, total_dist = nearest_neighbor_tsp(dist_matrix, start=0)
        algorithm_used = "nearest_neighbor"

    elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0

    # Calculate total duration from time matrix
    total_minutes = sum(
        time_matrix[route[i]][route[i + 1]]
        for i in range(len(route) - 1)
    ) if len(route) > 1 else 0.0

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
