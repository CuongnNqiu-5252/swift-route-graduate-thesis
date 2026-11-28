from core.distance import build_multi_objective_matrix


def test_road_costs_preserve_direction_and_weights():
    distances = [[0, 2], [6, 0]]
    durations = [[0, 12], [3, 0]]
    assert build_multi_objective_matrix(distances, durations, 0, 1) == distances
    assert build_multi_objective_matrix(distances, durations, 1, 0) == [[0, 6], [1.5, 0]]


def test_empty_matrix():
    assert build_multi_objective_matrix([], []) == []
