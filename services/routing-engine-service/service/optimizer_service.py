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
