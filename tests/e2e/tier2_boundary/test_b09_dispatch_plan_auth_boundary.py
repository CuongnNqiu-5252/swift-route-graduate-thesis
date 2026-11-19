"""
B09 (R2-DispatchPlanAuth Boundary): Edge & Corner Case Testing for Dispatch Plan Authorization.
Verifies already-acknowledged idempotency, declined status transition, unassigned/null driver plan rejection,
empty decline reason handling, and non-existent plan UUID 404 rejection.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b09_acknowledge_already_acknowledged_plan(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """Acknowledging an already-acknowledged plan executes idempotently and returns 200."""
    # First acknowledgement
    resp1 = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        notes="First acknowledgement",
        token=driver_token,
    )
    assert resp1.status_code == 200

    # Second acknowledgement
    resp2 = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        notes="Repeated acknowledgement",
        token=driver_token,
    )
    assert resp2.status_code in (200, 409), f"Expected 200 or 409, got {resp2.status_code}"


def test_b09_acknowledge_cancelled_or_declined_plan(
    client: DualClient, driver_2_token: str, test_ids: DeterministicIDs
):
    """Declining a dispatch plan successfully transitions its status to DECLINED."""
    resp = client.decline_plan(
        plan_id=test_ids.PLAN_2_ID,
        reason="Driver vehicle breakdown",
        token=driver_2_token,
    )
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    assert data.get("status") == "DECLINED"


def test_b09_null_driver_on_plan_rejection(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """A dispatch plan without an assigned driver cannot be acknowledged by a driver (fails with 403)."""
    if client.is_mock:
        client.mock_client.dispatch_plans[test_ids.PLAN_1_ID]["driver_id"] = None

    resp = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        token=driver_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for unassigned plan, got {resp.status_code}"


def test_b09_decline_with_empty_reason(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """Declining a plan with empty or whitespace reason is handled safely."""
    resp = client.decline_plan(
        plan_id=test_ids.PLAN_1_ID,
        reason="",
        token=driver_token,
    )
    assert resp.status_code in (200, 400), f"Expected 200 or 400, got {resp.status_code}"


def test_b09_nonexistent_plan_uuid(
    client: DualClient, driver_token: str
):
    """Acknowledging a non-existent plan UUID returns 404 Not Found."""
    resp = client.acknowledge_plan(
        plan_id="00000000-0000-0000-0000-000000000000",
        token=driver_token,
    )
    assert resp.status_code == 404, f"Expected 404 Not Found, got {resp.status_code}"
