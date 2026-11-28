"""
F02 (R1-TokenRevocation): Refresh Token Invalidation on Member Suspension.
Verifies that when member status != ACTIVE, all active refresh tokens are revoked
and user status is synchronized to SUSPENDED.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f02_suspend_member_revokes_refresh_tokens(client: DualClient, admin_token: str, test_ids: DeterministicIDs):
    """Suspending a member triggers revocation of active refresh tokens."""
    resp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OPERATOR_USER_ID,
        reason="Security audit revocation",
        token=admin_token,
    )
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    assert data.get("refresh_tokens_revoked") is True or data.get("status") == "SUSPENDED"


def test_f02_suspended_member_cannot_refresh_token(client: DualClient, admin_token: str, test_ids: DeterministicIDs):
    """Attempting token refresh with a revoked/suspended refresh token returns 401."""
    # First suspend operator
    client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OPERATOR_USER_ID,
        token=admin_token,
    )
    # Attempt refresh using operator's refresh token
    refresh_resp = client.refresh_token("operator_rt_valid_1")
    assert refresh_resp.status_code == 401, f"Expected 401 Unauthorized, got {refresh_resp.status_code}"


def test_f02_suspended_member_status_synchronized(client: DualClient, admin_token: str, test_ids: DeterministicIDs):
    """User status is synchronized to SUSPENDED upon member suspension."""
    resp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.DRIVER_USER_ID,
        token=admin_token,
    )
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    assert data.get("status") == "SUSPENDED"


def test_f02_deactivated_member_revokes_refresh_tokens(client: DualClient, owner_token: str, test_ids: DeterministicIDs):
    """Member revocation/deactivation invalidates active session tokens."""
    resp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.DRIVER_2_USER_ID,
        reason="Contract termination",
        token=owner_token,
    )
    assert resp.status_code == 200
    # Refresh should fail immediately
    refresh_resp = client.refresh_token("driver2_rt_valid_1")
    assert refresh_resp.status_code == 401


def test_f02_active_member_retains_refresh_token(client: DualClient, test_ids: DeterministicIDs):
    """An active member can successfully refresh their access token."""
    refresh_resp = client.refresh_token("owner_rt_valid_1")
    assert refresh_resp.status_code == 200, f"Expected 200 OK, got {refresh_resp.status_code}: {refresh_resp.text}"
    body = refresh_resp.json()
    data = body.get("data", body)
    assert "access_token" in data
    assert "refresh_token" in data
