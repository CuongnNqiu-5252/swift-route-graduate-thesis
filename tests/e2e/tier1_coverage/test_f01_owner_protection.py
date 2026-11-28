"""
F01 (R1-OwnerProtection): Privilege Escalation Prevention in TenantEmployeeService.
Verifies that TENANT_ADMIN cannot invite OWNER, suspend OWNER, or self-suspend,
while valid actions (OWNER inviting OWNER, TENANT_ADMIN suspending OPERATOR) succeed.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f01_tenant_admin_cannot_invite_owner(client: DualClient, admin_token: str, test_ids: DeterministicIDs):
    """TENANT_ADMIN cannot invite a user with the OWNER role (must fail with 403)."""
    resp = client.invite_member(
        tenant_id=test_ids.TENANT_A_ID,
        email="new_owner_candidate@example.com",
        role="OWNER",
        token=admin_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"
    body = resp.json()
    assert "Forbidden" in resp.text or body.get("status") == 403 or (body.get("error") and "FORBIDDEN" in str(body.get("error")))


def test_f01_owner_can_invite_owner(client: DualClient, owner_token: str, test_ids: DeterministicIDs):
    """OWNER can invite another user with OWNER role (must succeed with 200 or 201)."""
    resp = client.invite_member(
        tenant_id=test_ids.TENANT_A_ID,
        email="co_owner@example.com",
        role="OWNER",
        token=owner_token,
    )
    assert resp.status_code in (200, 201), f"Expected 200/201, got {resp.status_code}: {resp.text}"
    body = resp.json()
    data = body.get("data", body)
    assert data.get("role") == "OWNER" or "invitation_id" in data


def test_f01_tenant_admin_cannot_suspend_owner(client: DualClient, admin_token: str, test_ids: DeterministicIDs):
    """TENANT_ADMIN cannot suspend an OWNER account (must fail with 403)."""
    resp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OWNER_USER_ID,
        reason="Malicious privilege escalation attempt",
        token=admin_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"


def test_f01_tenant_admin_cannot_self_suspend(client: DualClient, admin_token: str, test_ids: DeterministicIDs):
    """TENANT_ADMIN cannot suspend their own account (must fail with 403)."""
    resp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.ADMIN_USER_ID,
        reason="Accidental self-lockout",
        token=admin_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"


def test_f01_tenant_admin_can_suspend_operator(client: DualClient, admin_token: str, test_ids: DeterministicIDs):
    """TENANT_ADMIN can suspend an OPERATOR (must succeed with 200)."""
    resp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OPERATOR_USER_ID,
        reason="Policy violation",
        token=admin_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}: {resp.text}"
    body = resp.json()
    data = body.get("data", body)
    assert data.get("status") == "SUSPENDED"
