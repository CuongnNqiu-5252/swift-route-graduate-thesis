"""
B08 (R4-AuthMultiTenantSession Boundary): Edge & Corner Case Testing for Multi-Tenant Sessions.
Verifies zero-membership handling, non-member tenant switch validation, deleted/suspended tenant fallback,
database role update propagation, and parameter precedence.
"""

import pytest
from jose import jwt

from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import JWT_SECRET, JWT_ALGORITHM, DeterministicIDs


def test_b08_user_with_zero_memberships_handling(
    client: DualClient, test_ids: DeterministicIDs
):
    """A user with no tenant memberships receives an unscoped token without arbitrary tenant assignment."""
    resp = client.refresh_token("unscoped_rt_valid_1")
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
    data = resp.json().get("data", resp.json())
    assert data.get("tenant_id") is None
    assert data.get("tenant_role") is None


def test_b08_switched_tenant_membership_validation(
    client: DualClient, test_ids: DeterministicIDs
):
    """Attempting to refresh a session into a tenant where the user has no active membership is rejected with 403."""
    # User belongs to Tenant A and B, but NOT Tenant Suspended
    resp = client.refresh_token("multitenant_rt_valid_1", tenant_id="99999999-9999-9999-9999-999999999999")
    assert resp.status_code == 403, f"Expected 403 Forbidden for non-member tenant refresh, got {resp.status_code}"


def test_b08_deleted_tenant_fallback(
    client: DualClient, test_ids: DeterministicIDs
):
    """Attempting to refresh into a SUSPENDED tenant fails with 403 Forbidden."""
    resp = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_SUSPENDED_ID)
    assert resp.status_code == 403, f"Expected 403 for suspended tenant session, got {resp.status_code}"


def test_b08_db_role_update_reflected_on_refresh(
    client: DualClient, test_ids: DeterministicIDs
):
    """When a user's role in a tenant changes in the database, subsequent refresh reflects the updated role."""
    if client.is_mock:
        # Promote multi-tenant user in Tenant A from DRIVER to OPERATOR
        client.mock_client.users[test_ids.MULTI_TENANT_USER_ID]["memberships"][test_ids.TENANT_A_ID] = "OPERATOR"

    resp = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    assert data.get("tenant_role") == "OPERATOR"


def test_b08_header_token_precedence(
    client: DualClient, test_ids: DeterministicIDs, token_factory
):
    """Explicit tenant_id parameter takes precedence over bearer token claim when switching tenants."""
    token_tenant_a = token_factory(
        user_id=test_ids.MULTI_TENANT_USER_ID,
        tenant_id=test_ids.TENANT_A_ID,
        tenant_role="DRIVER",
    )
    # Provide Tenant A token, but explicitly request Tenant B in refresh call
    resp = client.refresh_token(
        "multitenant_rt_valid_1",
        tenant_id=test_ids.TENANT_B_ID,
        token=token_tenant_a,
    )
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    assert data.get("tenant_id") == test_ids.TENANT_B_ID
    assert data.get("tenant_role") == "OPERATOR"
