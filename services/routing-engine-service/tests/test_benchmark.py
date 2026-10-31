import random
import time
from typing import Dict, List
import pytest

from core.distance import build_distance_matrix
from core.tsp_nearest_neighbor import nearest_neighbor_tsp
from core.tsp_held_karp import held_karp_tsp
from core.tsp_genetic import genetic_tsp


def generate_random_city_coords(n: int, seed: int = 42) -> List[tuple[float, float]]:
    """Generate n random GPS coordinates in Hanoi urban area."""
    random.seed(seed)
    # Approx Hanoi bounding box: lat ~ 21.00 - 21.05, lon ~ 105.80 - 105.86
    coords = []
    for _ in range(n):
        lat = 21.00 + random.random() * 0.05
        lon = 105.80 + random.random() * 0.06
        coords.append((lat, lon))
    return coords


def benchmark_tsp_algorithms(n: int = 10) -> Dict[str, dict]:
    """
    Run Nearest Neighbor, Genetic Algorithm, and Held-Karp DP on the same dataset
    to measure execution time, total distance, and gap to optimum.
    """
    coords = generate_random_city_coords(n, seed=123)
    dist_matrix = build_distance_matrix(coords)

    results = {}

    # 1. Exact Held-Karp DP (Ground Truth)
    start_time = time.perf_counter()
    hk_tour, hk_dist = held_karp_tsp(dist_matrix, start=0)
    hk_duration_ms = (time.perf_counter() - start_time) * 1000
    results["exact_held_karp"] = {
        "distance_km": hk_dist,
        "runtime_ms": hk_duration_ms,
        "gap_pct": 0.0,
        "tour": hk_tour,
    }

    # 2. Nearest Neighbor (Baseline 1)
    start_time = time.perf_counter()
    nn_tour, nn_dist = nearest_neighbor_tsp(dist_matrix, start=0)
    nn_duration_ms = (time.perf_counter() - start_time) * 1000
    nn_gap = ((nn_dist - hk_dist) / hk_dist) * 100.0 if hk_dist > 0 else 0.0
    results["nearest_neighbor"] = {
        "distance_km": nn_dist,
        "runtime_ms": nn_duration_ms,
        "gap_pct": nn_gap,
        "tour": nn_tour,
    }

    # 3. Genetic Algorithm (Baseline 2)
    start_time = time.perf_counter()
    ga_tour, ga_dist = genetic_tsp(dist_matrix, start=0, population_size=50, generations=150)
    ga_duration_ms = (time.perf_counter() - start_time) * 1000
    ga_gap = ((ga_dist - hk_dist) / hk_dist) * 100.0 if hk_dist > 0 else 0.0
    results["genetic_algorithm"] = {
        "distance_km": ga_dist,
        "runtime_ms": ga_duration_ms,
        "gap_pct": ga_gap,
        "tour": ga_tour,
    }

    return results


def test_benchmark_run():
    """Verify that all 3 algorithms execute successfully and provide expected comparative results."""
    results = benchmark_tsp_algorithms(n=8)

    hk = results["exact_held_karp"]
    nn = results["nearest_neighbor"]
    ga = results["genetic_algorithm"]

    # Held-Karp must be the absolute optimum
    assert hk["distance_km"] <= nn["distance_km"] + 1e-6
    assert hk["distance_km"] <= ga["distance_km"] + 1e-6

    # NN must be very fast (< 50ms)
    assert nn["runtime_ms"] < 50.0


if __name__ == "__main__":
    print("=== SWIFTROUTE TSP ALGORITHM BENCHMARK (N=10) ===")
    res = benchmark_tsp_algorithms(n=10)
    print(f"{'Algorithm':<22} | {'Distance (km)':<15} | {'Runtime (ms)':<15} | {'Gap to Opt (%)':<15}")
    print("-" * 75)
    for algo, data in res.items():
        print(
            f"{algo:<22} | {data['distance_km']:<15.3f} | {data['runtime_ms']:<15.2f} | {data['gap_pct']:<15.2f}%"
        )
