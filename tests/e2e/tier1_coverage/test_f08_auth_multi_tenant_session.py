"""
F08 (R4-AuthMultiTenantSession): Multi-Tenant Active Session Preservation on Token Refresh.
Verifies that when users belong to multiple tenants, refreshing an access token preserves
the active tenant session, tenant role, and rejects switching to inactive tenants.
"""

from jose import jwt
import pytest

from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import JWT_SECRET, JWT_ALGORITHM, DeterministicIDs


def test_f08_refresh_preserves_active_tenant_session(client: DualClient, test_ids: DeterministicIDs):
    """Token refresh explicitly requesting Tenant B preserves Tenant B in newly minted token."""
    resp = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_B_ID)
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}: {resp.text}"
    data = resp.json().get("data", resp.json())
    new_token = data["access_token"]
    claims = jwt.decode(new_token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims.get("tenant_id") == test_ids.TENANT_B_ID


def test_f08_refresh_preserves_tenant_role(client: DualClient, test_ids: DeterministicIDs):
    """Token refresh preserves the corresponding role for the selected tenant."""
    # Multi-tenant user is DRIVER in Tenant A and OPERATOR in Tenant B
    resp_a = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_a.status_code == 200
    claims_a = jwt.decode(resp_a.json()["data"]["access_token"], JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims_a.get("tenant_role") == "DRIVER"

    resp_b = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_B_ID)
    assert resp_b.status_code == 200
    claims_b = jwt.decode(resp_b.json()["data"]["access_token"], JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims_b.get("tenant_role") == "OPERATOR"


def test_f08_refresh_defaults_to_primary_if_unspecified(client: DualClient, test_ids: DeterministicIDs):
    """Token refresh without explicit tenant_id defaults to active primary tenant."""
    resp = client.refresh_token("multitenant_rt_valid_1")
    assert resp.status_code == 200
    claims = jwt.decode(resp.json()["data"]["access_token"], JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims.get("tenant_id") in (test_ids.TENANT_A_ID, test_ids.TENANT_B_ID)


def test_f08_refresh_rejects_inactive_tenant_session(client: DualClient, test_ids: DeterministicIDs):
    """Attempting to refresh into a suspended tenant session is rejected with 403."""
    resp = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_SUSPENDED_ID)
    assert resp.status_code == 403, f"Expected 403 Forbidden for suspended tenant session, got {resp.status_code}"


def test_f08_refresh_with_bearer_token_context(client: DualClient, test_ids: DeterministicIDs, token_factory):
    """Active tenant is inferred and preserved from current Bearer token when refreshing."""
    current_token = token_factory(
        user_id=test_ids.MULTI_TENANT_USER_ID,
        system_role="USER",
        tenant_id=test_ids.TENANT_B_ID,
        tenant_role="OPERATOR",
    )
    resp = client.refresh_token("multitenant_rt_valid_1", token=current_token)
    assert resp.status_code == 200
    claims = jwt.decode(resp.json()["data"]["access_token"], JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims.get("tenant_id") == test_ids.TENANT_B_ID
    assert claims.get("tenant_role") == "OPERATOR"
