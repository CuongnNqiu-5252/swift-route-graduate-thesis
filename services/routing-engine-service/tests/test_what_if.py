from api.schemas import LocationPoint, StopItem, WhatIfOptimizationRequest
from service.optimizer_service import simulate_what_if


def test_what_if_is_in_memory_and_compares_two_driver_counts():
    request = WhatIfOptimizationRequest(
        depot=LocationPoint(lat=10.7769, lng=106.7009),
        stops=[
            StopItem(id="a", location=LocationPoint(lat=10.7780, lng=106.7020)),
            StopItem(id="b", location=LocationPoint(lat=10.7797, lng=106.6990)),
            StopItem(id="c", location=LocationPoint(lat=10.7725, lng=106.6980)),
            StopItem(id="d", location=LocationPoint(lat=10.7828, lng=106.6958)),
        ],
        baseline_driver_count=3,
        scenario_driver_count=2,
    )

    result = simulate_what_if(request)

    assert result.baseline.driver_count == 3
    assert result.scenario.driver_count == 2
    assert result.baseline.algorithm_used == "held_karp"
    assert result.baseline.total_distance_km > 0
    assert result.scenario.completion_minutes > 0
    assert isinstance(result.completion_delta_percent, float)
