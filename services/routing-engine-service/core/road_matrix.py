"""
core/road_matrix.py
Road-network distance and travel-time matrix builder using Goong Distance Matrix API
(vehicle=bike), with automatic Haversine fallback when the API key is missing or
the external request fails.
"""
from __future__ import annotations

import logging
import os
from typing import List, Optional, Tuple

import httpx

from core.distance import build_distance_matrix, build_time_matrix, haversine

logger = logging.getLogger("uvicorn.error")
logging.getLogger("httpx").setLevel(logging.WARNING)

DEFAULT_GOONG_BASE_URL = "https://rsapi.goong.io"
DEFAULT_TIMEOUT_SECONDS = 4.0
# Goong Distance Matrix safely accepts 10x10 = 100 elements per request.
MAX_CHUNK_SIZE = 10


def _format_coords(locations: List[Tuple[float, float]]) -> str:
    return "|".join(f"{lat:.6f},{lng:.6f}" for lat, lng in locations)


def build_road_matrices(
    locations: List[Tuple[float, float]],
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    avg_speed_kmh: float = 28.0,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
) -> Tuple[List[List[float]], List[List[float]], str]:
    """
    Build N x N distance (km) and duration (minutes) matrices for a list of (lat, lng) points.

    Uses Goong Distance Matrix API (vehicle=bike) as primary source so one-way streets,
    bridges, and motorbike routing are reflected in route optimization and ETA.
    Falls back to Haversine + constant speed when GOONG_API_KEY is unset or Goong fails.

    Args:
        locations: List of (latitude, longitude) tuples.
        api_key: Optional Goong API key override (defaults to GOONG_API_KEY env var).
        base_url: Optional Goong base URL override (defaults to GOONG_BASE_URL env var).
        avg_speed_kmh: Fallback speed in km/h when Haversine fallback is used.
        timeout_seconds: HTTP timeout per Distance Matrix request.

    Returns:
        Tuple of (dist_matrix_km, time_matrix_minutes, distance_source).
    """
    n = len(locations)
    if n == 0:
        return [], [], "HAVERSINE_FALLBACK"
    if n == 1:
        return [[0.0]], [[0.0]], "GOONG_BIKE_DISTANCE_MATRIX"

    effective_key = (api_key if api_key is not None else os.getenv("GOONG_API_KEY", "")).strip()
    if not effective_key:
        dist_matrix = build_distance_matrix(locations)
        time_matrix = build_time_matrix(dist_matrix, avg_speed_kmh=avg_speed_kmh)
        return dist_matrix, time_matrix, "HAVERSINE_FALLBACK"

    effective_base = (base_url if base_url is not None else os.getenv("GOONG_BASE_URL", DEFAULT_GOONG_BASE_URL)).rstrip("/")
    endpoint = f"{effective_base}/DistanceMatrix"

    dist_matrix = [[0.0] * n for _ in range(n)]
    time_matrix = [[0.0] * n for _ in range(n)]
    speed_factor = 60.0 / avg_speed_kmh

    try:
        with httpx.Client(timeout=timeout_seconds) as client:
            for o_start in range(0, n, MAX_CHUNK_SIZE):
                origins = locations[o_start : o_start + MAX_CHUNK_SIZE]
                for d_start in range(0, n, MAX_CHUNK_SIZE):
                    destinations = locations[d_start : d_start + MAX_CHUNK_SIZE]
                    response = client.get(
                        endpoint,
                        params={
                            "origins": _format_coords(origins),
                            "destinations": _format_coords(destinations),
                            "vehicle": "bike",
                            "api_key": effective_key,
                        },
                    )
                    response.raise_for_status()
                    payload = response.json()
                    rows = payload.get("rows")
                    if not isinstance(rows, list) or len(rows) != len(origins):
                        raise ValueError("Invalid Goong DistanceMatrix rows payload")

                    for i_offset, row in enumerate(rows):
                        elements = row.get("elements") if isinstance(row, dict) else None
                        if not isinstance(elements, list) or len(elements) != len(destinations):
                            raise ValueError("Invalid Goong DistanceMatrix elements payload")

                        i = o_start + i_offset
                        for j_offset, elem in enumerate(elements):
                            j = d_start + j_offset
                            if i == j:
                                dist_matrix[i][j] = 0.0
                                time_matrix[i][j] = 0.0
                                continue

                            status = elem.get("status") if isinstance(elem, dict) else None
                            dist_val = (elem.get("distance") or {}).get("value") if isinstance(elem, dict) else None
                            dur_val = (elem.get("duration") or {}).get("value") if isinstance(elem, dict) else None

                            if status == "OK" and dist_val is not None and dur_val is not None:
                                dist_matrix[i][j] = max(0.0, float(dist_val) / 1000.0)
                                time_matrix[i][j] = max(0.0, float(dur_val) / 60.0)
                            else:
                                fallback_km = haversine(*locations[i], *locations[j])
                                dist_matrix[i][j] = fallback_km
                                time_matrix[i][j] = fallback_km * speed_factor

        return dist_matrix, time_matrix, "GOONG_BIKE_DISTANCE_MATRIX"
    except Exception as exc:
        logger.warning(
            "[RoutingEngine] Goong DistanceMatrix failed (%s); falling back to Haversine for %d points",
            exc,
            n,
        )
        dist_matrix = build_distance_matrix(locations)
        time_matrix = build_time_matrix(dist_matrix, avg_speed_kmh=avg_speed_kmh)
        return dist_matrix, time_matrix, "HAVERSINE_FALLBACK"
