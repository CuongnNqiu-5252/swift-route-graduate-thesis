"""Road-network matrices from Goong. Incomplete road data is never estimated."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed, TimeoutError as FuturesTimeoutError
import logging
import math
import os
import time
from typing import List, Optional, Tuple

import httpx

logging.getLogger("httpx").setLevel(logging.WARNING)
DEFAULT_GOONG_BASE_URL = "https://rsapi.goong.io"
DEFAULT_TIMEOUT_SECONDS = 4.0
MAX_CHUNK_SIZE = 10
MAX_CONCURRENT_REQUESTS = 8
DEFAULT_MATRIX_TIMEOUT_SECONDS = 20.0
SOURCE = "GOONG_BIKE_DISTANCE_MATRIX"


class RoadMatrixError(RuntimeError):
    """Road data is unavailable; callers must not produce an estimated route."""


def _format_coords(locations: List[Tuple[float, float]]) -> str:
    return "|".join(f"{lat:.6f},{lng:.6f}" for lat, lng in locations)


def _fetch_road_matrix(
    origins: List[Tuple[float, float]],
    destinations: List[Tuple[float, float]],
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    matrix_timeout_seconds: float = DEFAULT_MATRIX_TIMEOUT_SECONDS,
) -> Tuple[List[List[float]], List[List[float]], str]:
    """Fetch a directed road matrix for arbitrary origins x destinations within one time budget.

    Deduplicates coordinates at Goong's six-decimal request precision, then expands back to
    input order. Any failed request or invalid non-identical edge rejects the entire matrix.
    """
    if timeout_seconds <= 0 or matrix_timeout_seconds <= 0:
        raise ValueError("Matrix timeouts must be positive")
    if not origins or not destinations:
        zeros = [[0.0] * len(destinations) for _ in origins]
        return zeros, [row[:] for row in zeros], SOURCE

    origin_points = [(round(lat, 6), round(lng, 6)) for lat, lng in origins]
    dest_points = [(round(lat, 6), round(lng, 6)) for lat, lng in destinations]
    unique_origins = list(dict.fromkeys(origin_points))
    unique_dests = list(dict.fromkeys(dest_points))
    origin_lookup = {point: i for i, point in enumerate(unique_origins)}
    dest_lookup = {point: j for j, point in enumerate(unique_dests)}
    origin_indices = [origin_lookup[point] for point in origin_points]
    dest_indices = [dest_lookup[point] for point in dest_points]

    if len(set(unique_origins) | set(unique_dests)) <= 1:
        zeros = [[0.0] * len(destinations) for _ in origins]
        return zeros, [row[:] for row in zeros], SOURCE

    key = (api_key if api_key is not None else os.getenv("GOONG_API_KEY", "")).strip()
    if not key:
        raise RoadMatrixError("GOONG_API_KEY is required for road routing")
    base = (base_url if base_url is not None else os.getenv("GOONG_BASE_URL", DEFAULT_GOONG_BASE_URL)).rstrip("/")
    deadline = time.monotonic() + matrix_timeout_seconds
    n_origins = len(unique_origins)
    n_dests = len(unique_dests)
    distances = [[0.0] * n_dests for _ in range(n_origins)]
    durations = [[0.0] * n_dests for _ in range(n_origins)]

    def fetch(o_start: int, d_start: int):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RoadMatrixError("Goong road matrix deadline exceeded")
        origin_chunk = unique_origins[o_start:o_start + MAX_CHUNK_SIZE]
        dest_chunk = unique_dests[d_start:d_start + MAX_CHUNK_SIZE]
        try:
            # Worker-owned clients remain safe when the caller's deadline expires.
            with httpx.Client(timeout=min(timeout_seconds, remaining)) as client:
                response = client.get(f"{base}/DistanceMatrix", params={
                    "origins": _format_coords(origin_chunk),
                    "destinations": _format_coords(dest_chunk),
                    "vehicle": "bike", "api_key": key,
                })
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError):
            # URLs and HTTP exceptions can contain the secret API key.
            raise RoadMatrixError("Goong road matrix request failed") from None
        rows = payload.get("rows") if isinstance(payload, dict) else None
        if not isinstance(rows, list) or len(rows) != len(origin_chunk):
            raise RoadMatrixError("Invalid Goong road matrix rows")
        values = []
        for oi, row in enumerate(rows):
            elements = row.get("elements") if isinstance(row, dict) else None
            if not isinstance(elements, list) or len(elements) != len(dest_chunk):
                raise RoadMatrixError("Invalid Goong road matrix elements")
            for dj, elem in enumerate(elements):
                i, j = o_start + oi, d_start + dj
                if unique_origins[i] == unique_dests[j]:
                    continue
                try:
                    if not isinstance(elem, dict) or elem.get("status") != "OK":
                        raise ValueError()
                    distance = elem["distance"]["value"]
                    duration = elem["duration"]["value"]
                    if any(isinstance(v, bool) or not isinstance(v, (int, float))
                           or not math.isfinite(v) or v <= 0 for v in (distance, duration)):
                        raise ValueError()
                except (KeyError, TypeError, ValueError):
                    raise RoadMatrixError(f"Unavailable Goong road edge ({i}, {j})") from None
                values.append((i, j, distance / 1000.0, duration / 60.0))
        return values

    executor = ThreadPoolExecutor(max_workers=MAX_CONCURRENT_REQUESTS)
    pending = []
    try:
        pending = [executor.submit(fetch, o, d)
                   for o in range(0, n_origins, MAX_CHUNK_SIZE)
                   for d in range(0, n_dests, MAX_CHUNK_SIZE)]
        for future in as_completed(pending, timeout=max(0, deadline - time.monotonic())):
            for i, j, distance, duration in future.result():
                distances[i][j] = distance
                durations[i][j] = duration
    except FuturesTimeoutError:
        raise RoadMatrixError("Goong road matrix deadline exceeded") from None
    finally:
        for future in pending:
            future.cancel()
        executor.shutdown(wait=False, cancel_futures=True)
    return ([[distances[i][j] for j in dest_indices] for i in origin_indices],
            [[durations[i][j] for j in dest_indices] for i in origin_indices], SOURCE)


def build_road_matrices(
    locations: List[Tuple[float, float]],
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    matrix_timeout_seconds: float = DEFAULT_MATRIX_TIMEOUT_SECONDS,
) -> Tuple[List[List[float]], List[List[float]], str]:
    """Fetch a complete square N x N directed road matrix for a list of (lat, lng) points."""
    return _fetch_road_matrix(
        locations,
        locations,
        api_key=api_key,
        base_url=base_url,
        timeout_seconds=timeout_seconds,
        matrix_timeout_seconds=matrix_timeout_seconds,
    )


def build_open_road_matrices(
    origin: Tuple[float, float],
    stops: List[Tuple[float, float]],
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    matrix_timeout_seconds: float = DEFAULT_MATRIX_TIMEOUT_SECONDS,
) -> Tuple[List[List[float]], List[List[float]], str]:
    """Fetch an (N+1) x (N+1) open-path road matrix without requesting return-to-origin legs.

    Queries Goong only for ([origin] + stops) -> stops and fills column 0 (return to origin)
    with 0.0, saving API calls and preventing return-only one-way errors from failing open routes.
    """
    dist_sub, time_sub, source = _fetch_road_matrix(
        [origin] + stops,
        stops,
        api_key=api_key,
        base_url=base_url,
        timeout_seconds=timeout_seconds,
        matrix_timeout_seconds=matrix_timeout_seconds,
    )
    return (
        [[0.0] + row for row in dist_sub],
        [[0.0] + row for row in time_sub],
        source,
    )
