"""
B10 (R2-DriverPlansTenantBoundary Boundary): Edge & Corner Case Testing for Driver Plans Boundary.
Verifies multi-tenant driver clean separation, empty list serialization, suspended tenant handling,
header-token tenant mismatch rejection, and unscoped query defense.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b10_driver_in_multiple_tenants_clean_separation(
    client: DualClient, test_ids: DeterministicIDs, token_factory
):
    """A driver operating in multiple tenants only receives plans for the currently authenticated tenant."""
    contractor_tenant_a_token = token_factory(
        user_id=test_ids.MULTI_TENANT_USER_ID,
        tenant_id=test_ids.TENANT_A_ID,
        tenant_role="DRIVER",
    )
    resp = client.list_driver_plans(token=contractor_tenant_a_token)
    assert resp.status_code == 200
    plans = resp.json().get("data", resp.json())
    # All returned plans must belong strictly to Tenant A
    for plan in plans:
        assert plan.get("tenant_id") == test_ids.TENANT_A_ID


def test_b10_empty_plan_list_serialization(
    client: DualClient, tenant_b_driver_token: str, test_ids: DeterministicIDs
):
    """When no plans exist for a driver in a tenant, the response returns a valid empty list []."""
    if client.is_mock:
        # Clear Tenant B plans
        client.mock_client.dispatch_plans.pop(test_ids.PLAN_TENANT_B_ID, None)

    resp = client.list_driver_plans(token=tenant_b_driver_token)
    assert resp.status_code == 200
    plans = resp.json().get("data", resp.json())
    assert isinstance(plans, list)
    assert len(plans) == 0


def test_b10_suspended_tenant_returns_empty_or_forbidden(
    client: DualClient, test_ids: DeterministicIDs, token_factory
):
    """Querying plans under a SUSPENDED tenant returns an empty list [] or 403."""
    suspended_driver_token = token_factory(
        user_id=test_ids.DRIVER_USER_ID,
        tenant_id=test_ids.TENANT_SUSPENDED_ID,
        tenant_role="DRIVER",
    )
    resp = client.list_driver_plans(token=suspended_driver_token)
    assert resp.status_code in (200, 403)
    if resp.status_code == 200:
        plans = resp.json().get("data", resp.json())
        assert len(plans) == 0


def test_b10_header_param_tenant_mismatch_rejection(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """Supplying an X-Tenant-Id header differing from the JWT tenant claim is rejected with 403."""
    resp = client.get(
        "/api/orders/dispatch/driver-plans",
        headers={"X-Tenant-Id": test_ids.TENANT_B_ID},
        token=driver_token,  # Tenant A
    )
    assert resp.status_code == 403, f"Expected 403 for mismatched tenant header, got {resp.status_code}"


def test_b10_large_volume_pagination_or_unscoped(
    client: DualClient, unscoped_token: str
):
    """Querying driver plans without any tenant context is rejected with 403 Forbidden."""
    resp = client.list_driver_plans(token=unscoped_token)
    assert resp.status_code == 403, f"Expected 403 for unscoped token, got {resp.status_code}"
