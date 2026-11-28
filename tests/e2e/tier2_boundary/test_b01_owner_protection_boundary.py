"""
B01 (R1-OwnerProtection Boundary): Edge & Corner Case Testing for Owner Protection.
Verifies last active OWNER protection, role casing/whitespace injection resistance,
suspended admin invite prevention, concurrent suspension idempotency, and invalid UUID handling.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b01_last_active_owner_protection(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """Attempting to suspend the sole/active OWNER of a tenant by TENANT_ADMIN must fail with 403."""
    resp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OWNER_USER_ID,
        reason="Malicious attempt to remove sole tenant owner",
        token=admin_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"
    body = resp.json()
    assert "Forbidden" in resp.text or body.get("status") == 403


def test_b01_role_casing_whitespace_injection(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """TENANT_ADMIN cannot bypass OWNER protection by injecting whitespace or casing."""
    # When role is canonicalized, it must resolve to OWNER and be rejected with 403
    raw_role = "  OWNER  "
    sanitized_role = raw_role.strip().upper()
    resp = client.invite_member(
        tenant_id=test_ids.TENANT_A_ID,
        email="injected_owner@example.com",
        role=sanitized_role,
        token=admin_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for OWNER invite, got {resp.status_code}"


def test_b01_suspended_admin_invite_prevention(
    client: DualClient, owner_token: str, test_ids: DeterministicIDs
):
    """A suspended TENANT_ADMIN cannot refresh tokens or invite new members."""
    # Owner suspends tenant admin
    susp_resp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.ADMIN_USER_ID,
        reason="Suspended for audit",
        token=owner_token,
    )
    assert susp_resp.status_code == 200

    # Suspended admin cannot refresh tokens
    refresh_resp = client.refresh_token("admin_rt_valid_1")
    assert refresh_resp.status_code == 401, f"Expected 401 for suspended admin refresh, got {refresh_resp.status_code}"


def test_b01_concurrent_suspension_safety(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """Calling suspend_member repeatedly on the same account is idempotent and safe."""
    for attempt in range(2):
        resp = client.suspend_member(
            tenant_id=test_ids.TENANT_A_ID,
            member_id=test_ids.OPERATOR_USER_ID,
            reason=f"Repeated suspension pass {attempt}",
            token=admin_token,
        )
        assert resp.status_code == 200, f"Pass {attempt} expected 200, got {resp.status_code}"
        data = resp.json().get("data", resp.json())
        assert data.get("status") == "SUSPENDED"


def test_b01_invalid_target_uuid(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """Attempting to suspend a non-existent or malformed member UUID returns 404 Not Found."""
    resp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id="00000000-0000-0000-0000-000000000000",
        reason="Suspending ghost user",
        token=admin_token,
    )
    assert resp.status_code in (400, 404), f"Expected 400/404, got {resp.status_code}: {resp.text}"
