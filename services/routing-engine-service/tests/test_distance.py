import pytest
from core.distance import haversine, build_distance_matrix, build_time_matrix

def test_haversine_identical_point():
    # Distance between a point and itself must be 0.0
    dist = haversine(21.0285, 105.8542, 21.0285, 105.8542)
    assert pytest.approx(dist, abs=1e-6) == 0.0

def test_haversine_hanoi_to_hcm():
    # Hanoi (21.0285, 105.8542) to Ho Chi Minh City (10.8231, 106.6297)
    # The great circle distance is roughly 1,130 - 1,160 km
    dist = haversine(21.0285, 105.8542, 10.8231, 106.6297)
    assert 1100.0 <= dist <= 1200.0

def test_build_distance_matrix():
    coords = [
        (21.0285, 105.8542),  # Point A (Depot)
        (21.0300, 105.8500),  # Point B
        (21.0250, 105.8600),  # Point C
    ]
    dist_matrix = build_distance_matrix(coords)

    # Check dimensions
    assert len(dist_matrix) == 3
    assert all(len(row) == 3 for row in dist_matrix)

    # Diagonal must be 0
    for i in range(3):
        assert pytest.approx(dist_matrix[i][i], abs=1e-6) == 0.0

    # Symmetric: dist[i][j] == dist[j][i]
    for i in range(3):
        for j in range(3):
            assert pytest.approx(dist_matrix[i][j], abs=1e-6) == dist_matrix[j][i]
            assert dist_matrix[i][j] >= 0.0

def test_build_time_matrix():
    dist_matrix = [
        [0.0, 14.0],
        [14.0, 0.0],
    ]
    # At 28 km/h, 14 km should take exactly 30 minutes
    time_matrix = build_time_matrix(dist_matrix, avg_speed_kmh=28.0)
    assert pytest.approx(time_matrix[0][1], abs=1e-4) == 30.0
    assert pytest.approx(time_matrix[1][0], abs=1e-4) == 30.0
    assert time_matrix[0][0] == 0.0
