import pytest
from core.distance import build_distance_matrix, build_time_matrix, build_multi_objective_matrix
from api.schemas import BatchOptimizationRequest, LocationPoint, StopItem
from service.optimizer_service import solve_batch


def test_multi_objective_matrix_properties():
    locations = [
        (10.0371, 105.7828),  # Depot
        (10.0400, 105.7850),  # Stop 1
        (10.0500, 105.7900),  # Stop 2
    ]
    dist_mat = build_distance_matrix(locations)
    time_mat = build_time_matrix(dist_mat, avg_speed_kmh=30.0)

    # Time-heavy weights
    mat_time = build_multi_objective_matrix(dist_mat, time_mat, time_weight=0.9, cost_weight=0.1)
    # Cost-heavy weights
    mat_cost = build_multi_objective_matrix(dist_mat, time_mat, time_weight=0.1, cost_weight=0.9)

    assert len(mat_time) == 3
    assert len(mat_cost) == 3
    assert mat_time[0][0] == 0.0
    assert mat_time[0][1] > 0.0
    assert mat_cost[0][1] > 0.0


def test_solve_batch_with_custom_weights():
    depot = LocationPoint(lat=10.0371, lng=105.7828, address="Depot Kho Can Tho")
    stops = [
        StopItem(id="ord-1", location=LocationPoint(lat=10.0410, lng=105.7860)),
        StopItem(id="ord-2", location=LocationPoint(lat=10.0320, lng=105.7790)),
        StopItem(id="ord-3", location=LocationPoint(lat=10.0500, lng=105.7950)),
    ]

    req = BatchOptimizationRequest(
        depot=depot,
        stops=stops,
        algorithm="ortools",
        time_weight=0.8,
        cost_weight=0.2,
    )
    res = solve_batch(req)

    assert res.algorithm_used == "ortools"
    assert len(res.ordered_stops) == 3
    assert res.total_distance_km > 0.0
    assert res.total_duration_minutes > 0.0

