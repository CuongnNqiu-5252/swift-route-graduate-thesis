"""
core/tsp_ortools.py
Enterprise-grade Travelling Salesperson Problem (TSP) and Vehicle Routing Problem (VRP)
solvers powered by Google OR-Tools.

Supports:
  - Single-vehicle TSP with PATH_CHEAPEST_ARC first solution and GUIDED_LOCAL_SEARCH.
  - Multi-vehicle Capacitated Vehicle Routing Problem (CVRP) with vehicle capacity constraints.
"""
from __future__ import annotations

import logging
import math
from typing import Any, Dict, List, Tuple

from ortools.constraint_solver import pywrapcp, routing_enums_pb2

logger = logging.getLogger("uvicorn.error")

# Cost matrix scaling factor to convert floating point costs to 64-bit integers for OR-Tools
COST_SCALE = 1000
DEMAND_SCALE = 100
# Soft upper bound lateness penalties (per second) calibrated against COST_SCALE (1 km ~= 1000 units).
# Achievable windows (~6 km equivalent/min) outweigh moderate detours without exploding on 1s differences;
# already-overdue/unachievable stops (~0.3 km equivalent/min) never break achievable deadlines.
LATE_PENALTY_PER_SECOND = max(1, COST_SCALE // 10)
OVERDUE_LATE_PENALTY_PER_SECOND = max(1, COST_SCALE // 200)

# Soft arrival penalties (per second of delay beyond direct depot travel) for prioritized orders
# without explicit appointment windows.
# - URGENT (~2.4 km equivalent/min): pushes urgent orders near the start of the route while staying
#   below LATE_PENALTY_PER_SECOND (100) so achievable deadlines on other stops are never broken.
# - HIGH (~0.72 km equivalent/min): serves high-priority orders ahead of normal orders when the
#   extra detour is reasonable.
URGENT_ARRIVAL_PENALTY_PER_SECOND = max(1, COST_SCALE // 25)
HIGH_ARRIVAL_PENALTY_PER_SECOND = max(1, COST_SCALE // 80)


def _priority_arrival_penalty(priority: str | None) -> int:
    """Return per-second soft arrival penalty for an unwindowed stop based on its priority."""
    if not priority:
        return 0
    normalized = priority.strip().upper()
    if normalized == "URGENT":
        return URGENT_ARRIVAL_PENALTY_PER_SECOND
    if normalized == "HIGH":
        return HIGH_ARRIVAL_PENALTY_PER_SECOND
    return 0


def _priority_window_multiplier(priority: str | None) -> float:
    """Return lateness penalty multiplier when a stop with an appointment window is also prioritized."""
    if not priority:
        return 1.0
    normalized = priority.strip().upper()
    if normalized == "URGENT":
        return 2.0
    if normalized == "HIGH":
        return 1.5
    return 1.0


def ortools_tsp(
    cost_matrix: List[List[float]],
    start: int = 0,
    time_limit_seconds: float = 1.0,
) -> Tuple[List[int], float]:
    """
    Solve the Travelling Salesperson Problem (TSP) using Google OR-Tools.

    Args:
        cost_matrix: Symmetric or asymmetric N x N cost/distance matrix.
        start: Starting and ending vertex (depot index), default 0.
        time_limit_seconds: Search time limit in seconds (default 1.0s).

    Returns:
        A tuple of:
            - route: Sequence of stop indices starting and ending at 'start' (length N + 1).
            - total_cost: The sum of costs along the completed round-trip tour.

    Computational Complexity:
        - Initial Solution: O(N^2 log N) using Path Cheapest Arc heuristic.
        - Metaheuristic: Guided Local Search bounded strictly by `time_limit_seconds`.
        - Space: O(N^2) for the integer transit cost lookup.
    """
    n = len(cost_matrix)
    if n == 0:
        return [], 0.0
    if n == 1:
        return [start, start], 0.0
    if n == 2:
        other = 1 if start == 0 else 0
        dist = cost_matrix[start][other] + cost_matrix[other][start]
        return [start, other, start], dist

    # Prepare integer matrix for OR-Tools solver
    int_matrix = [
        [int(round(cost_matrix[i][j] * COST_SCALE)) for j in range(n)]
        for i in range(n)
    ]

    manager = pywrapcp.RoutingIndexManager(n, 1, start)
    routing = pywrapcp.RoutingModel(manager)

    def transit_callback(from_index: int, to_index: int) -> int:
        from_node = manager.IndexToNode(from_index)
        to_node = manager.IndexToNode(to_index)
        return int_matrix[from_node][to_node]

    transit_callback_index = routing.RegisterTransitCallback(transit_callback)
    routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

    search_parameters = pywrapcp.DefaultRoutingSearchParameters()
    search_parameters.first_solution_strategy = (
        routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
    )
    search_parameters.local_search_metaheuristic = (
        routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
    )

    # Set duration with millisecond precision
    total_ms = max(10, int(time_limit_seconds * 1000))
    search_parameters.time_limit.seconds = total_ms // 1000
    search_parameters.time_limit.nanos = (total_ms % 1000) * 1_000_000

    solution = routing.SolveWithParameters(search_parameters)

    if not solution:
        logger.warning(
            "⚠️ [OR-Tools TSP] Solver returned no solution; falling back to sequential order."
        )
        fallback_route = [start] + [i for i in range(n) if i != start] + [start]
        fallback_cost = sum(
            cost_matrix[fallback_route[i]][fallback_route[i + 1]]
            for i in range(len(fallback_route) - 1)
        )
        return fallback_route, fallback_cost

    # Reconstruct tour from solution
    route: List[int] = []
    total_cost: float = 0.0
    index = routing.Start(0)

    while not routing.IsEnd(index):
        node = manager.IndexToNode(index)
        route.append(node)
        prev_index = index
        index = solution.Value(routing.NextVar(index))
        next_node = manager.IndexToNode(index)
        total_cost += cost_matrix[node][next_node]

    # Append return to depot
    route.append(manager.IndexToNode(index))

    return route, total_cost


def ortools_vrp(
    cost_matrix: List[List[float]],
    demands: List[float],
    capacities: List[float],
    num_vehicles: int,
    start: int = 0,
    time_limit_seconds: float = 2.0,
    max_stops_per_vehicle: int | None = None,
    time_matrix: List[List[float]] | None = None,
    delivery_windows: List[Tuple[int, int] | None] | None = None,
    service_times: List[float] | None = None,
    priorities: List[str] | None = None,
) -> Dict[str, Any]:
    """
    Solve the Capacitated Vehicle Routing Problem (CVRP / VRPTW) using Google OR-Tools.

    Partitions stops across one or more vehicles while strictly respecting
    per-vehicle cargo capacity limits, stop-count balance, appointment windows,
    and order priority levels (NORMAL | HIGH | URGENT).

    Args:
        cost_matrix: N x N cost/distance matrix (index 0 is depot).
        demands: List of demand values per node (length N; depot demand is 0).
        capacities: Cargo capacity limits for each vehicle (length num_vehicles).
        num_vehicles: Number of available delivery vehicles / couriers.
        start: Depot node index (default 0).
        time_limit_seconds: Solver search time limit.
        max_stops_per_vehicle: Optional soft/hard cap on stops per vehicle.
        time_matrix: Optional N x N travel duration matrix in minutes.
        delivery_windows: Optional per-node (start_sec, end_sec) appointment windows.
        service_times: Optional per-node service duration in minutes.
        priorities: Optional per-node priority strings (NORMAL | HIGH | URGENT, length N).

    Returns:
        A dictionary containing:
            - routes: Dict[int, List[int]] -> Ordered node sequence for each vehicle.
            - vehicle_distances: Dict[int, float] -> Total distance per vehicle.
            - vehicle_loads: Dict[int, float] -> Total cargo weight per vehicle.
            - total_distance: float -> Fleet-wide total transit distance.
            - total_load: float -> Fleet-wide total cargo delivered.

    Raises:
        ValueError: If total demand exceeds total fleet capacity or if no feasible route exists.
    """
    n = len(cost_matrix)
    if num_vehicles <= 0:
        raise ValueError("num_vehicles must be at least 1")
    if len(capacities) != num_vehicles:
        raise ValueError(f"capacities count ({len(capacities)}) must match num_vehicles ({num_vehicles})")
    if len(demands) != n:
        raise ValueError(f"demands length ({len(demands)}) must match matrix size ({n})")
    if priorities is not None and len(priorities) != n:
        raise ValueError(f"priorities length ({len(priorities)}) must match matrix size ({n})")

    if service_times is None:
        service_times = [0.0] * n
    if len(service_times) != n or any(not math.isfinite(v) or v < 0 for v in service_times):
        raise ValueError("service_times must contain one finite nonnegative duration per node")
    service_seconds = [math.ceil(v * 60) for v in service_times]
    service_seconds[start] = 0

    total_demand = sum(demands)
    total_capacity = sum(capacities)
    if total_demand > total_capacity:
        raise ValueError(
            f"Infeasible VRP: total demand ({total_demand:.1f} kg) exceeds "
            f"fleet capacity ({total_capacity:.1f} kg)"
        )

    int_matrix = [
        [int(round(cost_matrix[i][j] * COST_SCALE)) for j in range(n)]
        for i in range(n)
    ]
    int_demands = [int(round(d * DEMAND_SCALE)) for d in demands]
    int_capacities = [int(round(c * DEMAND_SCALE)) for c in capacities]

    manager = pywrapcp.RoutingIndexManager(n, num_vehicles, start)
    routing = pywrapcp.RoutingModel(manager)

    def transit_callback(from_index: int, to_index: int) -> int:
        from_node = manager.IndexToNode(from_index)
        to_node = manager.IndexToNode(to_index)
        return int_matrix[from_node][to_node]

    transit_callback_index = routing.RegisterTransitCallback(transit_callback)
    routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

    def demand_callback(from_index: int) -> int:
        from_node = manager.IndexToNode(from_index)
        return int_demands[from_node]

    demand_callback_index = routing.RegisterUnaryTransitCallback(demand_callback)
    routing.AddDimensionWithVehicleCapacity(
        demand_callback_index,
        0,  # null capacity slack
        int_capacities,
        True,  # start cumul to zero
        "Capacity",
    )

    # Balance stop distribution across vehicles so all available drivers share the workload
    n_stops = max(0, n - 1)
    min_required_stops = (n_stops + num_vehicles - 1) // num_vehicles
    effective_max_stops = (
        max(max_stops_per_vehicle, min_required_stops)
        if max_stops_per_vehicle is not None and max_stops_per_vehicle > 0
        else max(1, min_required_stops + 1)
    )

    def stop_count_callback(from_index: int) -> int:
        from_node = manager.IndexToNode(from_index)
        return 0 if from_node == start else 1

    stop_count_callback_index = routing.RegisterUnaryTransitCallback(stop_count_callback)
    routing.AddDimensionWithVehicleCapacity(
        stop_count_callback_index,
        0,
        [effective_max_stops] * num_vehicles,
        True,
        "StopCount",
    )
    stop_count_dimension = routing.GetDimensionOrDie("StopCount")
    stop_count_dimension.SetGlobalSpanCostCoefficient(COST_SCALE * 5)

    # Activate the ArrivalTime dimension whenever appointment windows OR prioritized
    # orders (HIGH / URGENT) are present so the solver can penalize late arrivals.
    has_priority = priorities is not None and any(
        _priority_arrival_penalty(p) > 0 for idx, p in enumerate(priorities) if idx != start
    )
    if time_matrix is not None and (delivery_windows is not None or has_priority):
        if len(time_matrix) != n:
            raise ValueError(f"time_matrix size ({len(time_matrix)}) must match cost_matrix size ({n})")
        if delivery_windows is not None and len(delivery_windows) != n:
            raise ValueError(f"delivery_windows length ({len(delivery_windows)}) must match matrix size ({n})")

        effective_windows: List[Tuple[int, int] | None] = (
            delivery_windows if delivery_windows is not None else [None] * n
        )
        seconds = [[max(0, math.ceil(minutes * 60)) for minutes in row] for row in time_matrix]
        max_window_start = max((max(0, window[0]) for window in effective_windows if window), default=0)
        max_edge_sec = max((max(row, default=0) for row in seconds), default=0)
        horizon = max_window_start + n * max_edge_sec + sum(service_seconds) + 1

        def travel_seconds(from_index: int, to_index: int) -> int:
            source = manager.IndexToNode(from_index)
            destination = manager.IndexToNode(to_index)
            return service_seconds[source] + (0 if destination == start else seconds[source][destination])

        time_index = routing.RegisterTransitCallback(travel_seconds)
        routing.AddDimension(time_index, horizon, horizon, True, "ArrivalTime")
        arrival = routing.GetDimensionOrDie("ArrivalTime")
        arrival.SetSpanCostCoefficientForAllVehicles(1)
        for node in range(n):
            if node == start:
                continue
            window = effective_windows[node]
            node_priority = priorities[node] if priorities is not None else "NORMAL"
            index = manager.NodeToIndex(node)

            if window is not None:
                # Case 1: Stop has an explicit appointment window.
                # Enforce the earliest arrival (win_start) and penalize lateness past win_end,
                # scaling the penalty up if the stop is also marked HIGH (1.5x) or URGENT (2.0x).
                win_start = max(0, window[0])
                win_end = window[1]
                arrival.CumulVar(index).SetMin(win_start)

                multiplier = _priority_window_multiplier(node_priority)
                earliest_possible = max(win_start, seconds[start][node])
                if win_end >= earliest_possible:
                    # On-time delivery is achievable: prioritize meeting the window over moderate detours.
                    penalty = max(1, int(round(LATE_PENALTY_PER_SECOND * multiplier)))
                    arrival.SetCumulVarSoftUpperBound(index, win_end, penalty)
                else:
                    # Already overdue or unreachable on time: penalize only delay beyond earliest possible
                    # arrival with a gentle weight so it does not sacrifice achievable windows or mileage.
                    penalty = max(1, int(round(OVERDUE_LATE_PENALTY_PER_SECOND * multiplier)))
                    arrival.SetCumulVarSoftUpperBound(index, earliest_possible, penalty)
            else:
                # Case 2: Stop has no appointment window, but is marked HIGH or URGENT.
                # Set a soft upper bound at its direct travel time from the depot (seconds[start][node])
                # so every additional second spent visiting other stops before this one incurs a
                # priority-proportional waiting cost, pulling URGENT and HIGH stops toward the front.
                priority_penalty = _priority_arrival_penalty(node_priority)
                if priority_penalty > 0:
                    arrival.SetCumulVarSoftUpperBound(index, seconds[start][node], priority_penalty)

    search_parameters = pywrapcp.DefaultRoutingSearchParameters()
    search_parameters.first_solution_strategy = (
        routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
    )
    search_parameters.local_search_metaheuristic = (
        routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
    )

    total_ms = max(10, int(time_limit_seconds * 1000))
    search_parameters.time_limit.seconds = total_ms // 1000
    search_parameters.time_limit.nanos = (total_ms % 1000) * 1_000_000

    solution = routing.SolveWithParameters(search_parameters)

    if not solution:
        raise ValueError(
            "OR-Tools VRP solver could not find a feasible solution satisfying "
            "vehicle capacity and route constraints."
        )

    vehicle_routes: Dict[int, List[int]] = {}
    vehicle_distances: Dict[int, float] = {}
    vehicle_loads: Dict[int, float] = {}
    fleet_distance = 0.0
    fleet_load = 0.0

    for vehicle_id in range(num_vehicles):
        route: List[int] = []
        route_dist = 0.0
        route_load = 0.0
        index = routing.Start(vehicle_id)

        while not routing.IsEnd(index):
            node = manager.IndexToNode(index)
            route.append(node)
            route_load += demands[node]
            prev_index = index
            index = solution.Value(routing.NextVar(index))
            next_node = manager.IndexToNode(index)
            route_dist += cost_matrix[node][next_node]

        # Return to depot
        route.append(manager.IndexToNode(index))

        vehicle_routes[vehicle_id] = route
        vehicle_distances[vehicle_id] = round(route_dist, 3)
        vehicle_loads[vehicle_id] = round(route_load, 2)
        fleet_distance += route_dist
        fleet_load += route_load

    return {
        "routes": vehicle_routes,
        "vehicle_distances": vehicle_distances,
        "vehicle_loads": vehicle_loads,
        "total_distance": round(fleet_distance, 3),
        "total_load": round(fleet_load, 2),
    }

