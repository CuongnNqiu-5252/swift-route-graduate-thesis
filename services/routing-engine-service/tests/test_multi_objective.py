import pytest
from core.distance import build_multi_objective_matrix
from api.schemas import BatchOptimizationRequest, LocationPoint, StopItem
from service.optimizer_service import solve_batch

pytestmark = pytest.mark.usefixtures("goong_stub")


def test_multi_objective_matrix_properties():
    dist_mat = [[0, 2, 4], [3, 0, 1], [5, 2, 0]]
    time_mat = [[0, 5, 3], [6, 0, 2], [4, 7, 0]]

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


def test_solve_batch_multi_vehicle_cvrp_partitions_stops():
    depot = LocationPoint(lat=10.7769, lng=106.7009, address="Depot Q1")
    stops = [
        StopItem(id=f"ord-{i}", location=LocationPoint(lat=10.770 + i * 0.008, lng=106.680 + (i % 3) * 0.012), demand_kg=2.5)
        for i in range(6)
    ]

    req = BatchOptimizationRequest(
        depot=depot,
        stops=stops,
        algorithm="ortools",
        vehicle_count=3,
        max_stops_per_vehicle=3,
    )
    res = solve_batch(req)

    assert res.algorithm_used == "ortools"
    assert len(res.ordered_stops) == 6
    assert len(res.vehicle_routes) >= 2
    assigned_stop_ids = [s.stop_id for vr in res.vehicle_routes for s in vr.ordered_stops]
    assert sorted(assigned_stop_ids) == sorted(s.id for s in stops)
    for vr in res.vehicle_routes:
        assert vr.total_distance_km > 0.0
        assert vr.total_duration_minutes > 0.0
        assert vr.total_distance_km == vr.ordered_stops[-1].cumulative_distance_km
        assert vr.total_duration_minutes == vr.ordered_stops[-1].estimated_arrival_minutes + 5


def test_solve_batch_uses_open_route_from_depot(monkeypatch):
    """Verify open-route ordering & total distance omit any forced return-to-depot leg."""
    depot = LocationPoint(lat=10.0300, lng=105.7800, address="Depot")
    stops = [
        StopItem(id="far", location=LocationPoint(lat=10.0600, lng=105.7800)),
        StopItem(id="mid", location=LocationPoint(lat=10.0450, lng=105.7800)),
        StopItem(id="near", location=LocationPoint(lat=10.0350, lng=105.7800)),
    ]

    from service import optimizer_service
    matrix = [[0, 6, 3, 1], [0, 0, 3, 5], [0, 3, 0, 2], [0, 5, 2, 0]]
    monkeypatch.setattr(optimizer_service, "build_open_road_matrices", lambda _origin, _stops: (matrix, matrix, "test"))
    res = solve_batch(BatchOptimizationRequest(depot=depot, stops=stops, algorithm="ortools"))

    assert [s.stop_id for s in res.ordered_stops] == ["near", "mid", "far"]
    assert res.total_distance_km == res.ordered_stops[-1].cumulative_distance_km
    assert res.total_duration_minutes == res.ordered_stops[-1].estimated_arrival_minutes + 5



def test_measure_ordered_route_keeps_input_order_and_sums_open_path(monkeypatch):
    from api.schemas import BatchOptimizationRequest, LocationPoint, StopItem
    from service import optimizer_service

    monkeypatch.setattr(optimizer_service, "build_open_road_matrices", lambda _origin, _stops: (
        [[0, 2, 1], [0, 0, 4], [0, 4, 0]],
        [[0, 5, 2], [0, 0, 8], [0, 8, 0]],
        "test",
    ))
    request = BatchOptimizationRequest(
        depot=LocationPoint(lat=10, lng=105),
        stops=[
            StopItem(id="B", location=LocationPoint(lat=10.03, lng=105)),
            StopItem(id="A", location=LocationPoint(lat=10.01, lng=105)),
        ],
    )
    result = optimizer_service.measure_ordered_route(request)
    assert [stop.stop_id for stop in result.ordered_stops] == ["B", "A"]
    assert [stop.estimated_arrival_minutes for stop in result.ordered_stops] == [5, 18]
    assert result.total_distance_km == 6
    assert result.total_duration_minutes == 23
