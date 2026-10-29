import math
from typing import Any, Dict, List, Tuple
import pandas as pd

from core.distance import haversine
from core.scenario_generator import DeliveryScenario, generate_scenario
from core.tsp_held_karp import held_karp_tsp


def extract_step_features(
    u: int,
    v: int,
    mask: int,
    scenario: DeliveryScenario,
    dist_matrix: List[List[float]],
    time_matrix: List[List[float]],
    max_dist: float,
    current_time: float,
) -> Dict[str, float]:
    """
    Extract normalized, scale-invariant features for transitioning from node u to candidate v,
    given the current visited bitmask 'mask'.

    Features:
        - dist_uv_norm: Normalized distance between u and v [0, 1].
        - dist_v_depot_norm: Normalized distance from candidate v to the depot [0, 1].
        - dist_rank_norm: Rank of dist(u, v) among all unvisited candidate nodes [0, 1].
        - unvisited_ratio: Fraction of total stops remaining unvisited.
        - time_slack_norm: Normalized remaining time until v's deadline.
        - demand_ratio: Candidate demand relative to vehicle capacity.
        - angle_to_depot: Directional alignment between (u -> v) and (u -> depot) in radians [0, pi].
    """
    n = len(dist_matrix)
    dist_uv = dist_matrix[u][v]
    dist_uv_norm = dist_uv / max_dist if max_dist > 0 else 0.0

    dist_v_depot = dist_matrix[v][0]
    dist_v_depot_norm = dist_v_depot / max_dist if max_dist > 0 else 0.0

    # Unvisited candidates
    unvisited = [cand for cand in range(n) if not (mask & (1 << cand))]
    num_unvisited = len(unvisited)
    unvisited_ratio = num_unvisited / n if n > 0 else 0.0

    # Distance rank among unvisited nodes
    sorted_unvisited = sorted(unvisited, key=lambda cand: dist_matrix[u][cand])
    rank = sorted_unvisited.index(v) if v in sorted_unvisited else 0
    dist_rank_norm = rank / num_unvisited if num_unvisited > 1 else 0.0

    # Time window slack & demand
    # Note: stop id corresponds to index (index 0 is depot, index i >= 1 is stop i-1)
    if v == 0:
        time_slack_norm = 1.0
        demand_ratio = 0.0
    else:
        stop = scenario.stops[v - 1]
        arrival_time = current_time + time_matrix[u][v]
        slack = stop.time_window_end - arrival_time
        # Normalize slack with 8-hour workday (480 minutes)
        time_slack_norm = max(-1.0, min(2.0, slack / 480.0))
        demand_ratio = min(1.0, stop.demand / scenario.vehicle_capacity)

    # Angle difference between vector (u -> v) and vector (u -> depot)
    locs = scenario.get_locations()
    u_lat, u_lon = locs[u]
    v_lat, v_lon = locs[v]
    depot_lat, depot_lon = locs[0]

    vec_uv = (v_lat - u_lat, v_lon - u_lon)
    vec_ud = (depot_lat - u_lat, depot_lon - u_lon)

    dot = vec_uv[0] * vec_ud[0] + vec_uv[1] * vec_ud[1]
    norm_uv = math.hypot(vec_uv[0], vec_uv[1])
    norm_ud = math.hypot(vec_ud[0], vec_ud[1])

    if norm_uv > 1e-9 and norm_ud > 1e-9:
        cos_theta = max(-1.0, min(1.0, dot / (norm_uv * norm_ud)))
        angle_to_depot = math.acos(cos_theta) / math.pi  # Normalized to [0, 1]
    else:
        angle_to_depot = 0.5

    return {
        "dist_uv_norm": round(dist_uv_norm, 4),
        "dist_v_depot_norm": round(dist_v_depot_norm, 4),
        "dist_rank_norm": round(dist_rank_norm, 4),
        "unvisited_ratio": round(unvisited_ratio, 4),
        "time_slack_norm": round(time_slack_norm, 4),
        "demand_ratio": round(demand_ratio, 4),
        "angle_to_depot": round(angle_to_depot, 4),
    }


def generate_training_dataset(
    num_scenarios: int = 200,
    min_stops: int = 8,
    max_stops: int = 12,
    output_parquet_path: str = "data/dataset_v1.parquet",
    seed: int = 42,
) -> pd.DataFrame:
    """
    Generate ground-truth transition dataset for ML edge pruning.
    Solves each scenario with exact Held-Karp DP and labels optimal vs sub-optimal transitions.

    Args:
        num_scenarios: Number of simulated scenarios to generate and solve.
        min_stops: Minimum number of delivery stops per scenario.
        max_stops: Maximum number of delivery stops (kept <= 13 for fast exact DP).
        output_parquet_path: File path to save the dataset.
        seed: Random seed for reproducibility.

    Returns:
        A pandas DataFrame with feature columns and 'is_optimal' target label.
    """
    import random
    random.seed(seed)

    rows: List[Dict[str, Any]] = []

    for scen_idx in range(num_scenarios):
        n_stops = random.randint(min_stops, max_stops)
        scenario = generate_scenario(
            num_orders=n_stops,
            scenario_id=f"train_{scen_idx:04d}",
            seed=seed + scen_idx,
        )
        dist_mat, time_mat = scenario.build_matrices()

        # Solve exactly using Held-Karp
        optimal_tour, _ = held_karp_tsp(dist_mat, start=0)
        if not optimal_tour or len(optimal_tour) < n_stops + 2:
            continue

        # Maximum distance for normalization
        max_dist = max(max(row) for row in dist_mat)
        n = len(dist_mat)

        # Track visited mask along the optimal tour
        mask = 1 << 0  # Start at depot
        current_time = scenario.depot_open_time

        # For each step i in the tour, tour[i] is current, tour[i+1] is optimal next node
        for step in range(len(optimal_tour) - 2):
            curr_u = optimal_tour[step]
            optimal_v = optimal_tour[step + 1]

            # Candidate unvisited nodes
            for candidate_v in range(n):
                if not (mask & (1 << candidate_v)):
                    features = extract_step_features(
                        u=curr_u,
                        v=candidate_v,
                        mask=mask,
                        scenario=scenario,
                        dist_matrix=dist_mat,
                        time_matrix=time_mat,
                        max_dist=max_dist,
                        current_time=current_time,
                    )
                    features["scenario_id"] = scenario.scenario_id
                    features["u"] = curr_u
                    features["v"] = candidate_v
                    features["is_optimal"] = 1 if candidate_v == optimal_v else 0
                    rows.append(features)

            # Advance to optimal next node
            mask |= (1 << optimal_v)
            current_time += time_mat[curr_u][optimal_v]
            if optimal_v > 0:
                current_time += scenario.stops[optimal_v - 1].service_time

    df = pd.DataFrame(rows)

    if output_parquet_path:
        import os
        os.makedirs(os.path.dirname(output_parquet_path), exist_ok=True)
        df.to_parquet(output_parquet_path, index=False)

    return df
