import math
from typing import List, Tuple

def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """
    Calculate the great-circle distance between two GPS coordinates
    on Earth using the Haversine formula.

    Args:
        lat1: Latitude of point 1 (decimal degrees).
        lon1: Longitude of point 1 (decimal degrees).
        lat2: Latitude of point 2 (decimal degrees).
        lon2: Longitude of point 2 (decimal degrees).

    Returns:
        Distance in kilometers (km).
    """
    # Earth's mean radius in kilometers (IUGG standard)
    earth_radius_km = 6371.0

    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    )
    # Clamp 'a' to [0.0, 1.0] to avoid potential domain errors with asin due to float rounding
    a = min(1.0, max(0.0, a))
    c = 2.0 * math.asin(math.sqrt(a))

    return earth_radius_km * c


def build_distance_matrix(locations: List[Tuple[float, float]]) -> List[List[float]]:
    """
    Construct an N x N distance matrix for all pairs of locations.

    Args:
        locations: List of (latitude, longitude) tuples.

    Returns:
        Symmetric N x N matrix where dist_matrix[i][j] is the distance
        in kilometers from location i to location j.
    """
    n = len(locations)
    matrix = [[0.0] * n for _ in range(n)]

    for i in range(n):
        lat1, lon1 = locations[i]
        for j in range(i + 1, n):
            lat2, lon2 = locations[j]
            dist = haversine(lat1, lon1, lat2, lon2)
            matrix[i][j] = dist
            matrix[j][i] = dist

    return matrix


def build_time_matrix(
    dist_matrix: List[List[float]], avg_speed_kmh: float = 28.0
) -> List[List[float]]:
    """
    Convert an N x N distance matrix to a travel time matrix.

    Args:
        dist_matrix: N x N distance matrix in kilometers.
        avg_speed_kmh: Average vehicle speed in km/h (default 28.0 km/h for urban delivery).

    Returns:
        N x N matrix where time_matrix[i][j] is the travel time in minutes.
    """
    if avg_speed_kmh <= 0:
        raise ValueError("Average speed must be strictly positive.")

    # time (minutes) = (distance_km / speed_kmh) * 60.0
    factor = 60.0 / avg_speed_kmh
    return [[dist * factor for dist in row] for row in dist_matrix]


def build_multi_objective_matrix(
    dist_matrix: List[List[float]],
    time_matrix: List[List[float]],
    time_weight: float = 0.5,
    cost_weight: float = 0.5,
) -> List[List[float]]:
    """
    Construct an N x N normalized multi-objective cost matrix combining delivery time
    and distance/transportation cost:
        Cost(u, v) = W_time * NormalizedTime(u, v) + W_cost * NormalizedDistance(u, v)

    Both metrics are normalized against their maximum edge values to make them strictly comparable:
        NormalizedTime(u, v) = time_matrix[u][v] / max_time
        NormalizedDistance(u, v) = dist_matrix[u][v] / max_dist

    The resulting matrix is scaled by max_dist so that distances remain in a familiar metric space.
    """
    n = len(dist_matrix)
    if n == 0:
        return []

    max_dist = max((max(row) for row in dist_matrix), default=0.0)
    max_time = max((max(row) for row in time_matrix), default=0.0)

    # Normalize weights so they sum to 1.0
    total_w = time_weight + cost_weight
    w_time = time_weight / total_w if total_w > 0 else 0.5
    w_cost = cost_weight / total_w if total_w > 0 else 0.5

    cost_matrix = [[0.0] * n for _ in range(n)]

    for i in range(n):
        for j in range(n):
            if i == j:
                continue
            norm_dist = (dist_matrix[i][j] / max_dist) if max_dist > 0 else 0.0
            norm_time = (time_matrix[i][j] / max_time) if max_time > 0 else 0.0
            # Normalized combined score in [0.0, 1.0], scaled back by max_dist
            cost_matrix[i][j] = (w_cost * norm_dist + w_time * norm_time) * (max_dist if max_dist > 0 else 1.0)

    return cost_matrix
