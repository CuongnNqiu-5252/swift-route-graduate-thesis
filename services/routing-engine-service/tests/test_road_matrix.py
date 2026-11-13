from __future__ import annotations

import httpx
import pytest

from core.road_matrix import MAX_CHUNK_SIZE, build_road_matrices


def test_build_road_matrices_falls_back_when_api_key_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("GOONG_API_KEY", raising=False)
    coords = [(10.0371, 105.7828), (10.0299, 105.7706)]

    dist_m, time_m, source = build_road_matrices(coords, api_key="")

    assert source == "HAVERSINE_FALLBACK"
    assert len(dist_m) == 2
    assert dist_m[0][0] == 0.0
    assert dist_m[0][1] > 1.0
    assert pytest.approx(time_m[0][1], rel=1e-4) == dist_m[0][1] * (60.0 / 28.0)


def test_build_road_matrices_uses_goong_asymmetric_bike_distances(monkeypatch: pytest.MonkeyPatch) -> None:
    captured_params = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured_params.append(dict(request.url.params))
        return httpx.Response(
            200,
            json={
                "rows": [
                    {
                        "elements": [
                            {"status": "OK", "distance": {"value": 0}, "duration": {"value": 0}},
                            {"status": "OK", "distance": {"value": 4200}, "duration": {"value": 540}},
                        ]
                    },
                    {
                        "elements": [
                            {"status": "OK", "distance": {"value": 5100}, "duration": {"value": 660}},
                            {"status": "OK", "distance": {"value": 0}, "duration": {"value": 0}},
                        ]
                    },
                ]
            },
        )

    transport = httpx.MockTransport(handler)
    real_client = httpx.Client

    def mock_client(*args, **kwargs):
        kwargs["transport"] = transport
        return real_client(*args, **kwargs)

    monkeypatch.setattr(httpx, "Client", mock_client)

    coords = [(10.0371, 105.7828), (10.0299, 105.7706)]
    dist_m, time_m, source = build_road_matrices(coords, api_key="test-key", base_url="https://mock.goong.io")

    assert source == "GOONG_BIKE_DISTANCE_MATRIX"
    assert captured_params[0]["vehicle"] == "bike"
    assert captured_params[0]["api_key"] == "test-key"
    assert dist_m == [[0.0, 4.2], [5.1, 0.0]]
    assert time_m == [[0.0, 9.0], [11.0, 0.0]]


def test_build_road_matrices_chunks_large_location_lists(monkeypatch: pytest.MonkeyPatch) -> None:
    call_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        origins = request.url.params["origins"].split("|")
        destinations = request.url.params["destinations"].split("|")
        rows = [
            {
                "elements": [
                    {"status": "OK", "distance": {"value": 2000}, "duration": {"value": 300}}
                    for _ in destinations
                ]
            }
            for _ in origins
        ]
        return httpx.Response(200, json={"rows": rows})

    transport = httpx.MockTransport(handler)
    real_client = httpx.Client

    def mock_client(*args, **kwargs):
        kwargs["transport"] = transport
        return real_client(*args, **kwargs)

    monkeypatch.setattr(httpx, "Client", mock_client)

    n = MAX_CHUNK_SIZE + 2
    coords = [(10.0 + i * 0.001, 105.7 + i * 0.001) for i in range(n)]
    dist_m, time_m, source = build_road_matrices(coords, api_key="test-key")

    assert source == "GOONG_BIKE_DISTANCE_MATRIX"
    assert call_count == 4  # 2x2 chunk grid for N > MAX_CHUNK_SIZE
    assert len(dist_m) == n
    assert dist_m[0][0] == 0.0
    assert dist_m[0][n - 1] == 2.0
    assert time_m[0][n - 1] == 5.0


def test_build_road_matrices_falls_back_on_http_error(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, json={"error": "upstream unavailable"})

    transport = httpx.MockTransport(handler)
    real_client = httpx.Client

    def mock_client(*args, **kwargs):
        kwargs["transport"] = transport
        return real_client(*args, **kwargs)

    monkeypatch.setattr(httpx, "Client", mock_client)

    coords = [(10.0371, 105.7828), (10.0299, 105.7706)]
    dist_m, time_m, source = build_road_matrices(coords, api_key="test-key")

    assert source == "HAVERSINE_FALLBACK"
    assert dist_m[0][1] > 0.0
    assert time_m[0][1] > 0.0
