"""
B04 (R3-GatewayTenantIsolation Boundary): Edge & Corner Case Testing for Gateway Tenant Isolation.
Verifies header formatting validation, SQL injection payload resistance, empty string normalization,
SYSTEM_ADMIN malformed UUID handling, and duplicate header line rejection.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b04_malformed_non_uuid_header_rejected(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """Supplying a malformed non-UUID string in X-Tenant-Id header is rejected with 400 or 403."""
    resp = client.get(
        f"/api/tenants/{test_ids.TENANT_A_ID}",
        headers={"X-Tenant-Id": "not-a-valid-uuid-string"},
        token=unscoped_token,
    )
    assert resp.status_code in (400, 403), f"Expected 400/403, got {resp.status_code}: {resp.text}"


def test_b04_sql_injection_in_header_rejected(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """SQL injection payloads in X-Tenant-Id header are safely rejected with 400 or 403."""
    injection_payload = "' OR '1'='1' --"
    resp = client.get(
        f"/api/tenants/{test_ids.TENANT_A_ID}",
        headers={"X-Tenant-Id": injection_payload},
        token=unscoped_token,
    )
    assert resp.status_code in (400, 403), f"Expected 400/403 for SQLi attempt, got {resp.status_code}"


def test_b04_empty_string_header_normalization(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """Empty string X-Tenant-Id header is normalized and does not allow bypass to tenant resources."""
    resp = client.get(
        f"/api/tenants/{test_ids.TENANT_A_ID}",
        headers={"X-Tenant-Id": ""},
        token=unscoped_token,
    )
    assert resp.status_code in (400, 403), f"Expected 400/403, got {resp.status_code}"


def test_b04_system_admin_with_malformed_uuid(
    client: DualClient, system_admin_token: str
):
    """SYSTEM_ADMIN providing a malformed non-existent tenant UUID returns 400 or 404 cleanly."""
    resp = client.get(
        "/api/tenants/invalid-nonexistent-uuid",
        headers={"X-Tenant-Id": "invalid-nonexistent-uuid"},
        token=system_admin_token,
    )
    assert resp.status_code in (400, 404), f"Expected 400/404, got {resp.status_code}"


def test_b04_duplicate_header_lines(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """Providing duplicate or comma-separated tenant headers across boundaries is rejected with 400 or 403."""
    resp = client.get(
        f"/api/tenants/{test_ids.TENANT_A_ID}",
        headers={"X-Tenant-Id": f"{test_ids.TENANT_A_ID}, {test_ids.TENANT_B_ID}"},
        token=admin_token,
    )
    assert resp.status_code in (400, 403), f"Expected 400/403, got {resp.status_code}"
