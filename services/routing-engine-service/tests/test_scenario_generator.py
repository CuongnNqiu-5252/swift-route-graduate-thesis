import pytest
from core.scenario_generator import generate_scenario, DeliveryScenario, CustomerStop

def test_generate_scenario_structure():
    n_orders = 10
    scenario = generate_scenario(num_orders=n_orders, seed=42)

    assert isinstance(scenario, DeliveryScenario)
    assert len(scenario.stops) == n_orders

    # Verify stop properties
    for stop in scenario.stops:
        assert isinstance(stop, CustomerStop)
        assert stop.id >= 1
        assert stop.demand > 0.0
        assert stop.time_window_start < stop.time_window_end
        assert stop.service_time > 0.0

    # Locations must include Depot at index 0 + n_orders stops
    locations = scenario.get_locations()
    assert len(locations) == n_orders + 1
    assert locations[0] == (scenario.depot_lat, scenario.depot_lon)


def test_build_matrices_from_scenario():
    scenario = generate_scenario(num_orders=5, seed=123)
    dist_mat, time_mat = scenario.build_matrices(avg_speed_kmh=30.0)

    # 5 orders + 1 depot = 6x6 matrix
    assert len(dist_mat) == 6
    assert len(dist_mat[0]) == 6
    assert len(time_mat) == 6
    assert len(time_mat[0]) == 6

    # Verify symmetry and diagonals
    for i in range(6):
        assert pytest.approx(dist_mat[i][i], abs=1e-6) == 0.0
        assert pytest.approx(time_mat[i][i], abs=1e-6) == 0.0
        for j in range(6):
            assert pytest.approx(dist_mat[i][j], abs=1e-6) == dist_mat[j][i]
            assert pytest.approx(time_mat[i][j], abs=1e-6) == time_mat[j][i]


def test_reproducibility_with_seed():
    scen1 = generate_scenario(num_orders=8, seed=999)
    scen2 = generate_scenario(num_orders=8, seed=999)

    assert scen1.get_locations() == scen2.get_locations()
    assert [s.demand for s in scen1.stops] == [s.demand for s in scen2.stops]
