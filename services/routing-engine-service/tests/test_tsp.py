import itertools
import pytest
from core.tsp_nearest_neighbor import nearest_neighbor_tsp
from core.tsp_held_karp import held_karp_tsp

def brute_force_tsp(dist_matrix: list[list[float]], start: int = 0) -> tuple[list[int], float]:
    """Helper: Exact brute-force solver using all permutations of unvisited nodes."""
    n = len(dist_matrix)
    if n <= 1:
        return [start, start], 0.0

    other_nodes = [i for i in range(n) if i != start]
    min_cost = float("inf")
    best_tour = []

    for perm in itertools.permutations(other_nodes):
        tour = [start] + list(perm) + [start]
        cost = sum(dist_matrix[tour[i]][tour[i + 1]] for i in range(len(tour) - 1))
        if cost < min_cost:
            min_cost = cost
            best_tour = tour

    return best_tour, min_cost


def test_nearest_neighbor_basic_square():
    # Square graph with side length 1, diagonals sqrt(2)
    # Node 0: (0, 0), Node 1: (0, 1), Node 2: (1, 1), Node 3: (1, 0)
    # dist 0-1: 1, 1-2: 1, 2-3: 1, 3-0: 1
    # diagonals 0-2: 1.414, 1-3: 1.414
    dist_matrix = [
        [0.0, 1.0, 1.414, 1.0],
        [1.0, 0.0, 1.0, 1.414],
        [1.414, 1.0, 0.0, 1.0],
        [1.0, 1.414, 1.0, 0.0],
    ]
    tour, dist = nearest_neighbor_tsp(dist_matrix, start=0)

    # Valid TSP tour must visit all 4 nodes and return to 0
    assert len(tour) == 5
    assert tour[0] == 0 and tour[-1] == 0
    assert set(tour) == {0, 1, 2, 3}
    assert dist == 4.0


def test_held_karp_vs_brute_force_random_matrix():
    # Symmetric 5-node distance matrix
    dist_matrix = [
        [0.0, 12.0, 29.0, 22.0, 13.0],
        [12.0, 0.0, 19.0, 3.0, 25.0],
        [29.0, 19.0, 0.0, 21.0, 23.0],
        [22.0, 3.0, 21.0, 0.0, 4.0],
        [13.0, 25.0, 23.0, 4.0, 0.0],
    ]

    bf_tour, bf_dist = brute_force_tsp(dist_matrix, start=0)
    hk_tour, hk_dist = held_karp_tsp(dist_matrix, start=0)

    # Held-Karp must guarantee absolute exact optimality matching brute force
    assert pytest.approx(hk_dist, abs=1e-6) == bf_dist
    assert len(hk_tour) == 6
    assert hk_tour[0] == 0 and hk_tour[-1] == 0
    assert set(hk_tour) == {0, 1, 2, 3, 4}


def test_held_karp_beats_or_equals_nearest_neighbor():
    # Asymmetric or tricky matrix where greedy choice traps Nearest Neighbor
    dist_matrix = [
        [0.0, 2.0, 10.0, 10.0],
        [2.0, 0.0, 1.0, 10.0],
        [10.0, 1.0, 0.0, 1.0],
        [10.0, 10.0, 1.0, 0.0],
    ]
    _, nn_dist = nearest_neighbor_tsp(dist_matrix, start=0)
    _, hk_dist = held_karp_tsp(dist_matrix, start=0)

    # Exact DP cost must always be <= greedy heuristic cost
    assert hk_dist <= nn_dist


def test_genetic_algorithm_finds_valid_tour():
    from core.tsp_genetic import genetic_tsp

    dist_matrix = [
        [0.0, 10.0, 15.0, 20.0],
        [10.0, 0.0, 35.0, 25.0],
        [15.0, 35.0, 0.0, 30.0],
        [20.0, 25.0, 30.0, 0.0],
    ]
    ga_tour, ga_dist = genetic_tsp(dist_matrix, start=0, population_size=40, generations=100)
    hk_tour, hk_dist = held_karp_tsp(dist_matrix, start=0)

    # GA must return a valid tour visiting all nodes and returning to depot
    assert len(ga_tour) == 5
    assert ga_tour[0] == 0 and ga_tour[-1] == 0
    assert set(ga_tour) == {0, 1, 2, 3}
    # On small N=4, GA easily finds the exact optimal solution
    assert pytest.approx(ga_dist, abs=1e-4) == hk_dist

