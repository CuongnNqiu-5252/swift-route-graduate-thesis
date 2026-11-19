"""
B11 (R2-SimulationNoRestrictions Boundary): Edge & Corner Case Testing for Driver Simulations.
Verifies idempotent stop operations, speed multiplier / start simulation handling,
plan tracking SSE stream connectivity, driver simulation stream connectivity, and unauthenticated rejection.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b11_idempotent_stop_on_stopped_simulation(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """Calling stop on an already stopped tracking simulation succeeds idempotently (returns 200)."""
    for _ in range(2):
        resp = client.stop_tracking_simulation(test_ids.PLAN_1_ID, token=driver_token)
        assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
        data = resp.json().get("data", resp.json())
        assert data.get("simulation") == "STOPPED"


def test_b11_speed_multiplier_start_boundary(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER can start tracking simulation without encountering privilege restrictions."""
    resp = client.start_tracking_simulation(test_ids.PLAN_1_ID, token=driver_token)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
    data = resp.json().get("data", resp.json())
    assert data.get("simulation") == "STARTED"


def test_b11_sse_plan_tracking_stream_connect(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER can connect to plan tracking SSE stream and receives text/event-stream content."""
    resp = client.get_plan_tracking_stream(test_ids.PLAN_1_ID, token=driver_token)
    assert resp.status_code == 200
    assert "text/event-stream" in resp.headers.get("Content-Type", "")
    assert "data:" in resp.text


def test_b11_driver_simulation_stream_connect(
    client: DualClient, driver_token: str
):
    """DRIVER can connect to global driver simulation stream with 200 OK and valid event stream."""
    resp = client.get_driver_simulation_stream(token=driver_token)
    assert resp.status_code == 200
    assert "text/event-stream" in resp.headers.get("Content-Type", "")
    assert "SIMULATION_ACTIVE" in resp.text


def test_b11_unauthenticated_simulation_rejected(
    client: DualClient, test_ids: DeterministicIDs
):
    """Simulation operations without valid authentication are rejected with 401 Unauthorized."""
    resp = client.start_tracking_simulation(test_ids.PLAN_1_ID, token=None)
    assert resp.status_code == 401, f"Expected 401 for unauthenticated simulation, got {resp.status_code}"
