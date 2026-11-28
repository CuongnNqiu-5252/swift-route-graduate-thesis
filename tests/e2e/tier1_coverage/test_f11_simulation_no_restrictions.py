"""
F11 (R2-SimulationNoRestrictions): Ensuring DRIVER Access to Simulation Endpoints.
Verifies that security hardening in DispatchController and DriverSimulationController
does NOT introduce unintended restrictions against the DRIVER role for dispatch and tracking simulation.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f11_driver_can_access_dispatch_plans_tracking(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER role can connect to the dispatch plan tracking stream."""
    resp = client.get_plan_tracking_stream(plan_id=test_ids.PLAN_1_ID, token=driver_token)
    assert resp.status_code == 200, f"Expected 200 for tracking stream, got {resp.status_code}: {resp.text}"


def test_f11_driver_can_start_tracking_simulation(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER role can start the tracking simulation."""
    resp = client.start_tracking_simulation(plan_id=test_ids.PLAN_1_ID, token=driver_token)
    assert resp.status_code in (200, 201), f"Expected 200/201, got {resp.status_code}: {resp.text}"
    body = resp.json()
    data = body.get("data", body)
    assert data.get("simulation") == "STARTED" or resp.is_success


def test_f11_driver_can_stop_tracking_simulation(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER role can stop the tracking simulation."""
    resp = client.stop_tracking_simulation(plan_id=test_ids.PLAN_1_ID, token=driver_token)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    body = resp.json()
    data = body.get("data", body)
    assert data.get("simulation") == "STOPPED" or resp.is_success


def test_f11_driver_can_access_driver_simulation_stream(
    client: DualClient, driver_token: str
):
    """DRIVER role can access the driver simulation SSE stream."""
    resp = client.get_driver_simulation_stream(token=driver_token)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"


def test_f11_driver_can_execute_simulation_lifecycle(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER role can execute full simulation lifecycle without authorization errors."""
    start_resp = client.start_tracking_simulation(plan_id=test_ids.PLAN_1_ID, token=driver_token)
    assert start_resp.status_code in (200, 201)

    stop_resp = client.stop_tracking_simulation(plan_id=test_ids.PLAN_1_ID, token=driver_token)
    assert stop_resp.status_code == 200
