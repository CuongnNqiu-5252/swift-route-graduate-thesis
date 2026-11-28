"""
F09 (R2-DispatchPlanAuth): Authorization & Driver Scoping on Dispatch Plans.
Verifies that POST /api/orders/dispatch/plans/{planId}/acknowledge and decline endpoints
strictly require tenant matching and caller identity (assigned DRIVER or OWNER/SYSTEM_ADMIN).
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f09_assigned_driver_can_acknowledge_plan(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """The assigned DRIVER can acknowledge their dispatched plan."""
    resp = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        notes="Accepted assignment, starting pre-trip inspection",
        token=driver_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}: {resp.text}"
    body = resp.json()
    data = body.get("data", body)
    assert data.get("status") == "ACKNOWLEDGED"


def test_f09_assigned_driver_can_decline_plan(
    client: DualClient, driver_2_token: str, test_ids: DeterministicIDs
):
    """The assigned DRIVER can decline their dispatched plan with a reason."""
    resp = client.decline_plan(
        plan_id=test_ids.PLAN_2_ID,
        reason="Vehicle mechanical fault",
        token=driver_2_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}: {resp.text}"
    body = resp.json()
    data = body.get("data", body)
    assert data.get("status") == "DECLINED"


def test_f09_unassigned_driver_cannot_acknowledge(
    client: DualClient, driver_2_token: str, test_ids: DeterministicIDs
):
    """A driver not assigned to the plan cannot acknowledge it (fails with 403)."""
    resp = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,  # Assigned to DRIVER_USER_ID, not DRIVER_2
        notes="Unauthorized acceptance attempt",
        token=driver_2_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"


def test_f09_cross_tenant_driver_cannot_acknowledge(
    client: DualClient, tenant_b_driver_token: str, test_ids: DeterministicIDs
):
    """A driver from another tenant cannot acknowledge a plan (fails with 403 or 404)."""
    resp = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,  # Tenant A plan
        notes="Cross-tenant breach attempt",
        token=tenant_b_driver_token,  # Tenant B driver
    )
    assert resp.status_code in (403, 404), f"Expected 403/404, got {resp.status_code}: {resp.text}"


def test_f09_tenant_admin_or_owner_can_acknowledge(
    client: DualClient, owner_token: str, test_ids: DeterministicIDs
):
    """An OWNER can override and acknowledge a dispatch plan administratively."""
    resp = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        notes="Administrative approval by owner",
        token=owner_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}: {resp.text}"
