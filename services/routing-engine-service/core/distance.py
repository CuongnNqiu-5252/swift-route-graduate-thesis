from typing import List


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
