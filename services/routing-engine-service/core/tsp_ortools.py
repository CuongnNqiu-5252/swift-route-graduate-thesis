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
from typing import Any, Dict, List, Tuple

from ortools.constraint_solver import pywrapcp, routing_enums_pb2

logger = logging.getLogger("uvicorn.error")

# Cost matrix scaling factor to convert floating point costs to 64-bit integers for OR-Tools
COST_SCALE = 1000
DEMAND_SCALE = 100


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
) -> Dict[str, Any]:
    """
    Solve the Capacitated Vehicle Routing Problem (CVRP) using Google OR-Tools.

    Partitions stops across multiple vehicles while strictly respecting
    per-vehicle cargo capacity limits, stop-count balance, and minimizing total transit distance.

    Args:
        cost_matrix: N x N cost/distance matrix (index 0 is depot).
        demands: List of demand values per node (length N; depot demand is 0).
        capacities: Cargo capacity limits for each vehicle (length num_vehicles).
        num_vehicles: Number of available delivery vehicles / couriers.
        start: Depot node index (default 0).
        time_limit_seconds: Solver search time limit.
        max_stops_per_vehicle: Optional soft/hard cap on stops per vehicle.

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

