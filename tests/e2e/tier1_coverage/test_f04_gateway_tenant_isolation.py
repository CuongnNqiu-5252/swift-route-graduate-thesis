"""
F04 (R3-GatewayTenantIsolation): Header Isolation & Anti-Spoofing in JwtAuthFilter.
Verifies that users without a tenant claim cannot supply arbitrary X-Tenant-Id headers
unless they possess the SYSTEM_ADMIN role.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f04_user_without_tenant_rejected_with_arbitrary_tenant_header(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """Non-admin user whose JWT lacks tenant_id supplying arbitrary X-Tenant-Id is rejected with 403."""
    resp = client.get(
        "/api/tenants/" + test_ids.TENANT_A_ID,
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=unscoped_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"
    body = resp.json()
    assert "Forbidden" in resp.text or body.get("status") == 403


def test_f04_system_admin_permitted_arbitrary_tenant_header(
    client: DualClient, system_admin_token: str, test_ids: DeterministicIDs
):
    """SYSTEM_ADMIN can supply arbitrary X-Tenant-Id header to manage any tenant."""
    resp = client.get(
        f"/api/tenants/{test_ids.TENANT_A_ID}",
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=system_admin_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}: {resp.text}"


def test_f04_tenant_user_with_matching_header_permitted(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """A tenant user supplying an X-Tenant-Id header matching their JWT tenant_id is permitted."""
    resp = client.get(
        f"/api/tenants/{test_ids.TENANT_A_ID}",
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=admin_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}: {resp.text}"


def test_f04_tenant_user_with_cross_tenant_header_rejected(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """A tenant user supplying an X-Tenant-Id header for a different tenant is rejected with 403."""
    resp = client.get(
        f"/api/tenants/{test_ids.TENANT_B_ID}",
        headers={"X-Tenant-Id": test_ids.TENANT_B_ID},
        token=admin_token,  # Belongs to Tenant A
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"


def test_f04_user_without_tenant_permitted_on_unscoped_routes(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """A user without tenant context can invoke unscoped provisioning/workflow poll endpoints."""
    resp = client.poll_provisioning(tenant_id=test_ids.TENANT_PENDING_ID, token=unscoped_token)
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}: {resp.text}"
