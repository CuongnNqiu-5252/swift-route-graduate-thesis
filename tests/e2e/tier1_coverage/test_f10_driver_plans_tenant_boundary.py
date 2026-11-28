"""
F10 (R2-DriverPlansTenantBoundary): Strict Tenant Scoping on Driver Plans Retrieval.
Verifies that GET /api/orders/dispatch/driver-plans enforces tenant boundary
and never leaks plans belonging to other tenants.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f10_list_driver_plans_scoped_to_caller_tenant(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """Driver plan retrieval returns only plans belonging to the caller's tenant."""
    resp = client.list_driver_plans(token=driver_token)
    assert resp.status_code == 200
    body = resp.json()
    plans = body.get("data", body)
    assert isinstance(plans, list)
    for p in plans:
        assert p.get("tenant_id") == test_ids.TENANT_A_ID


def test_f10_cross_tenant_plans_excluded(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """Plans from Tenant B are never returned to a Tenant A driver."""
    resp = client.list_driver_plans(token=driver_token)
    assert resp.status_code == 200
    plans = resp.json().get("data", resp.json())
    plan_ids = [p.get("id") for p in plans]
    assert test_ids.PLAN_TENANT_B_ID not in plan_ids


def test_f10_driver_plans_requires_tenant_context(
    client: DualClient, unscoped_token: str
):
    """Calling driver plans without tenant context is rejected with 403."""
    resp = client.list_driver_plans(token=unscoped_token)
    assert resp.status_code in (401, 403)


def test_f10_driver_plans_empty_for_inactive_tenant(
    client: DualClient, test_ids: DeterministicIDs, token_factory
):
    """A driver associated with a suspended tenant returns an empty list or 403."""
    suspended_token = token_factory(
        user_id="suspended-driver-id",
        tenant_id=test_ids.TENANT_SUSPENDED_ID,
        tenant_role="DRIVER",
    )
    resp = client.list_driver_plans(token=suspended_token)
    if resp.status_code == 200:
        data = resp.json().get("data", resp.json())
        assert len(data) == 0
    else:
        assert resp.status_code in (401, 403)


def test_f10_driver_plans_repository_tenant_scoping(
    client: DualClient, tenant_b_driver_token: str, test_ids: DeterministicIDs
):
    """Tenant B driver listing plans receives only Tenant B plans."""
    resp = client.list_driver_plans(token=tenant_b_driver_token)
    assert resp.status_code == 200
    plans = resp.json().get("data", resp.json())
    for p in plans:
        assert p.get("tenant_id") == test_ids.TENANT_B_ID
