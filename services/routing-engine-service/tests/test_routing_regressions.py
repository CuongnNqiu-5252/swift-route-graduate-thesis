import httpx
import pytest

from api.schemas import LocationPoint, StopItem
from core.road_matrix import build_road_matrices
from service.optimizer_service import _trace_open_route


def test_missing_key_never_calls_http(monkeypatch):
    def forbidden(*args, **kwargs):
        pytest.fail("HTTP must not be used without credentials")
    monkeypatch.setattr(httpx, "Client", forbidden)
    with pytest.raises(RuntimeError, match="GOONG_API_KEY"):
        build_road_matrices([(10, 105), (11, 106)], api_key="")


def test_invalid_edge_never_becomes_free_or_geographic(monkeypatch):
    real_client = httpx.Client
    transport = httpx.MockTransport(lambda request: httpx.Response(200, json={
        "rows": [{"elements": [{"status": "ZERO_RESULTS"}] * 2}] * 2,
    }))
    monkeypatch.setattr(httpx, "Client", lambda **kw: real_client(transport=transport, **kw))
    with pytest.raises(RuntimeError, match="edge"):
        build_road_matrices([(10, 105), (11, 106)], api_key="test")


@pytest.mark.parametrize("windows", [None, [None, (300, 900), (0, 600)]])
def test_trace_includes_service_after_arrival_and_final_service(windows):
    stops = [StopItem(id=str(i), location=LocationPoint(lat=10, lng=105), service_time=7)
             for i in range(2)]
    matrix = [[0, 5, 5], [5, 0, 5], [5, 5, 0]]
    ordered, _, duration, _ = _trace_open_route([0, 1, 2, 0], stops, matrix, matrix, windows)
    assert [stop.estimated_arrival_minutes for stop in ordered] == [5, 17]
    assert ordered[-1].late_by_minutes == (7 if windows else 0)
    assert duration == 24


def test_service_time_changes_solver_order_to_meet_window():
    from core.tsp_ortools import ortools_vrp
    # Distance favors 1 first; its 10-minute service would make 2 late.
    costs = [[0, 1, 2], [0, 0, 1], [0, 1, 0]]
    times = [[0, 1, 1], [0, 0, 1], [0, 1, 0]]
    result = ortools_vrp(costs, [0, 1, 1], [10], 1, time_matrix=times,
                         delivery_windows=[None, None, (0, 300)],
                         service_times=[0, 10, 1], time_limit_seconds=0.1)
    assert result["routes"][0] == [0, 2, 1, 0]


def test_duplicate_coordinates_expand_directed_road_data(monkeypatch):
    real_client = httpx.Client
    captured = []

    def handler(request):
        captured.append(dict(request.url.params))
        return httpx.Response(200, json={"rows": [
            {"elements": [{"status": "OK", "distance": {"value": d}, "duration": {"value": t}}
                          for d, t in row]}
            for row in [[(0, 0), (2000, 300)], [(4000, 600), (0, 0)]]
        ]})

    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(httpx, "Client", lambda **kw: real_client(transport=transport, **kw))
    distances, durations, _ = build_road_matrices([(10, 105), (11, 106), (10.0000001, 105)], api_key="test")
    assert len(captured) == 1
    assert len(captured[0]["origins"].split("|")) == 2
    assert distances == [[0, 2, 0], [4, 0, 4], [0, 2, 0]]
    assert durations == [[0, 5, 0], [10, 0, 10], [0, 5, 0]]


def test_chunks_run_concurrently_for_50_points(monkeypatch):
    import threading
    from core.road_matrix import MAX_CONCURRENT_REQUESTS
    barrier = threading.Barrier(MAX_CONCURRENT_REQUESTS)
    lock = threading.Lock()
    real_client = httpx.Client
    calls = 0

    def handler(request):
        nonlocal calls
        with lock:
            calls += 1
            current = calls
        if current <= MAX_CONCURRENT_REQUESTS:
            barrier.wait(timeout=3)
        origins = request.url.params["origins"].split("|")
        destinations = request.url.params["destinations"].split("|")
        return httpx.Response(200, json={"rows": [{"elements": [
            {"status": "OK", "distance": {"value": 2000}, "duration": {"value": 300}}
            for _ in destinations]} for _ in origins]})

    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(httpx, "Client", lambda **kw: real_client(transport=transport, **kw))
    distances, _, _ = build_road_matrices([(10 + i / 1000, 105) for i in range(50)], api_key="test")
    assert calls == 25
    assert len(distances) == 50
    assert distances[49][0] == 2


def test_total_deadline_does_not_wait_for_inflight_requests(monkeypatch):
    import threading
    import time
    from core.road_matrix import RoadMatrixError
    release = threading.Event()
    real_client = httpx.Client

    def handler(request):
        release.wait(timeout=2)
        return httpx.Response(503)

    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(httpx, "Client", lambda **kw: real_client(transport=transport, **kw))
    start = time.monotonic()
    try:
        with pytest.raises(RoadMatrixError, match="deadline"):
            build_road_matrices([(10 + i / 1000, 105) for i in range(50)], api_key="test",
                                matrix_timeout_seconds=0.05)
        assert time.monotonic() - start < 1
    finally:
        release.set()


@pytest.mark.parametrize("value", [None, -1, 0, float("inf"), float("nan"), "2000", True])
def test_invalid_road_values_reject_entire_matrix(monkeypatch, value):
    import json
    real_client = httpx.Client
    transport = httpx.MockTransport(lambda request: httpx.Response(200, content=json.dumps({"rows": [
        {"elements": [{"status": "OK", "distance": {"value": value}, "duration": {"value": 300}}] * 2}
    ] * 2})))
    monkeypatch.setattr(httpx, "Client", lambda **kw: real_client(transport=transport, **kw))
    with pytest.raises(RuntimeError, match="edge"):
        build_road_matrices([(10, 105), (11, 106)], api_key="test")


def test_service_time_survives_protobuf_and_defaults():
    from api.grpc_gen import routing_pb2
    from service.grpc_server import _to_stop_item_schema, _to_route_stop_state_schema
    location = routing_pb2.LocationPointProto(lat=10, lng=105)
    for message, convert in [(routing_pb2.StopItemProto, _to_stop_item_schema),
                             (routing_pb2.RouteStopStateProto, _to_route_stop_state_schema)]:
        assert convert(message(id="a", location=location)).service_time == 5
        for minutes in [0, 7.5]:
            encoded = message(id="a", location=location, service_time=minutes).SerializeToString()
            assert convert(message.FromString(encoded)).service_time == minutes


def test_rest_reports_missing_road_data_as_503(monkeypatch):
    import os
    # main loads the local .env at import; keep those values out of other tests.
    monkeypatch.setattr(os, "environ", os.environ.copy())
    from fastapi.testclient import TestClient
    from api.tenant_security import require_tenant_access
    from main import app
    monkeypatch.setenv("GOONG_API_KEY", "")
    app.dependency_overrides[require_tenant_access] = lambda: None
    try:
        response = TestClient(app).post("/optimize/measure", json={
            "depot": {"lat": 10, "lng": 105},
            "stops": [{"id": "a", "location": {"lat": 11, "lng": 106}}],
        })
        assert response.status_code == 503
        assert "GOONG_API_KEY" in response.json()["detail"]
    finally:
        app.dependency_overrides.pop(require_tenant_access, None)


def test_reoptimize_one_remaining_stop_includes_travel_and_service(goong_stub):
    from api.schemas import RouteReoptimizeRequest, RouteStopState
    from service.optimizer_service import solve_reoptimize
    result = solve_reoptimize(RouteReoptimizeRequest(
        driver_location=LocationPoint(lat=10, lng=105),
        current_stops=[RouteStopState(id="a", location=LocationPoint(lat=11, lng=106), service_time=7)],
    ))
    assert result.total_duration_minutes == 12
    assert result.ordered_stops[0].estimated_arrival_minutes == 5


def test_waiting_precedes_service_and_delays_next_stop():
    stops = [StopItem(id=str(i), location=LocationPoint(lat=10, lng=105), service_time=7)
             for i in range(2)]
    matrix = [[0, 5, 5], [5, 0, 5], [5, 5, 0]]
    ordered, _, duration, _ = _trace_open_route(
        [0, 1, 2, 0], stops, matrix, matrix, [None, (600, 900), (0, 1200)]
    )
    assert [stop.estimated_arrival_minutes for stop in ordered] == [10, 22]
    assert ordered[1].late_by_minutes == 2
    assert duration == 29


@pytest.mark.parametrize("locations", [[], [(10, 105)], [(10, 105)] * 3])
def test_no_road_lookup_needed_for_at_most_one_unique_point(monkeypatch, locations):
    def forbidden(*args, **kwargs):
        pytest.fail("No HTTP needed when all points coincide")
    monkeypatch.setattr(httpx, "Client", forbidden)
    distances, durations, _ = build_road_matrices(locations, api_key="")
    assert distances == [[0.0] * len(locations) for _ in locations]
    assert durations == distances


def test_fifteen_deliveries_include_all_ninety_minutes_of_service():
    stops = [StopItem(id=str(i), location=LocationPoint(lat=10, lng=105), service_time=6)
             for i in range(15)]
    matrix = [[0 if i == j else 1 for j in range(16)] for i in range(16)]
    ordered, _, duration, _ = _trace_open_route(list(range(16)), stops, matrix, matrix)
    assert ordered[-1].estimated_arrival_minutes == 99
    assert duration == 105
