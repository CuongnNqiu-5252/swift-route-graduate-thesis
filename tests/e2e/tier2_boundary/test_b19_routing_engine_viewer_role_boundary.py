"""
B19 (R4-RoutingEngineViewerRole Boundary): Edge & Corner Case Testing for Routing Role Scoping.
Verifies VIEWER role block from batch and insert optimization, VIEWER permission on routing plans read,
DRIVER permission preservation on reoptimize, and unauthenticated fail-closed defense.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b19_viewer_blocked_from_batch_optimize(
    client: DualClient, viewer_token: str
):
    """The VIEWER role is strictly blocked from invoking POST /optimize/batch (fails with 403 Forbidden)."""
    resp = client.optimize_batch(
        batch_data={"orders": [{"id": "ord-1"}], "vehicles": [{"id": "veh-1"}]},
        token=viewer_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for VIEWER batch optimize, got {resp.status_code}"


def test_b19_viewer_blocked_from_insert_optimize(
    client: DualClient, viewer_token: str
):
    """The VIEWER role is strictly blocked from invoking POST /optimize/insert (fails with 403 Forbidden)."""
    resp = client.optimize_insert(
        insert_data={"route_id": "rt-1", "order_id": "ord-new"},
        token=viewer_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for VIEWER insert optimize, got {resp.status_code}"


def test_b19_viewer_allowed_on_routing_plans_read(
    client: DualClient, viewer_token: str
):
    """The VIEWER role is permitted to read pre-calculated routing plans (succeeds with 200 OK)."""
    resp = client.get_routing_plans(token=viewer_token)
    assert resp.status_code == 200, f"Expected 200 OK for VIEWER plans read, got {resp.status_code}"
    data = resp.json().get("data", resp.json())
    assert isinstance(data, list)


def test_b19_driver_allowed_on_reoptimize(
    client: DualClient, driver_token: str
):
    """The DRIVER role is permitted to trigger route re-optimization during shifts (returns 200 OK)."""
    resp = client.optimize_reoptimize(
        reoptimize_data={"plan_id": "plan-01", "current_location": {"lat": 10.77, "lng": 106.70}},
        token=driver_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK for DRIVER reoptimize, got {resp.status_code}"
    data = resp.json().get("data", resp.json())
    assert data.get("algorithm_used") == "dp_ml_pruning"


def test_b19_unauthenticated_optimization_returns_401(
    client: DualClient
):
    """Calling optimization endpoints without authentication or internal key returns 401 Unauthorized."""
    resp = client.optimize_reoptimize(
        reoptimize_data={"plan_id": "plan-01"},
        token=None,
    )
    assert resp.status_code == 401, f"Expected 401 for unauthenticated reoptimize, got {resp.status_code}"
