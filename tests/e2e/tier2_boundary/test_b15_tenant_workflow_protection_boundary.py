"""
B15 (R3-TenantWorkflowProtection Boundary): Edge & Corner Case Testing for Tenant Workflow Protection.
Verifies path traversal protection, non-existent tenant workflow handling, creator polling post-approval,
foreign tenant polling rejection, and unscoped user KYC isolation.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b15_path_traversal_in_tenant_url_blocked(
    client: DualClient, unscoped_token: str
):
    """Path traversal sequences (e.g. ..%2fadmin/kyc) in tenant URLs are rejected with 400, 403, or 404."""
    traversal_paths = [
        "/api/tenants/..%2fadmin/kyc",
        "/api/tenants/....//kyc",
        "/api/tenants/%2e%2e%2fworkflow",
    ]
    for path in traversal_paths:
        resp = client.get(path, token=unscoped_token)
        assert resp.status_code in (400, 403, 404), f"Path traversal not blocked for {path}: {resp.status_code}"


def test_b15_deleted_or_nonexistent_tenant_onboarding(
    client: DualClient, system_admin_token: str
):
    """Accessing workflow details for a non-existent tenant returns 404 Not Found."""
    resp = client.get_tenant_workflow("00000000-0000-0000-0000-000000000000", token=system_admin_token)
    assert resp.status_code in (403, 404), f"Expected 403/404, got {resp.status_code}"


def test_b15_creator_polling_post_approval(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """The creator of a tenant can poll provisioning status even before explicit tenant assignment."""
    resp = client.poll_provisioning(test_ids.TENANT_PENDING_ID, token=unscoped_token)
    assert resp.status_code == 200, f"Expected 200 OK for creator polling, got {resp.status_code}"
    data = resp.json().get("data", resp.json())
    assert data.get("provisioning_status") == "READY"


def test_b15_foreign_creator_polling_rejected(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """A user who is not the creator of the tenant cannot poll its provisioning status (fails with 403)."""
    resp = client.poll_provisioning(test_ids.TENANT_A_ID, token=unscoped_token)
    assert resp.status_code == 403, f"Expected 403 Forbidden for foreign tenant polling, got {resp.status_code}"


def test_b15_unscoped_user_kyc_read_rejected(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """An unscoped user without a tenant claim cannot inspect KYC documents of arbitrary tenants."""
    resp = client.get_tenant_kyc(test_ids.TENANT_A_ID, token=unscoped_token)
    assert resp.status_code == 403, f"Expected 403 Forbidden for unscoped KYC read, got {resp.status_code}"
