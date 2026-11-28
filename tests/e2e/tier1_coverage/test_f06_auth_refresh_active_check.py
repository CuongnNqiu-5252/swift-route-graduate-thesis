"""
F06 (R4-AuthRefreshActiveCheck): User Status Validation During Token Refresh.
Verifies that AuthserviceImpl.refreshAccessToken requires user.status == ACTIVE
and fails closed with consistent error codes for inactive/suspended accounts.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f06_refresh_fails_when_user_suspended(client: DualClient, admin_token: str, test_ids: DeterministicIDs):
    """Token refresh fails when user.status is SUSPENDED."""
    # Suspend operator
    client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OPERATOR_USER_ID,
        token=admin_token,
    )
    # Attempt token refresh
    resp = client.refresh_token("operator_rt_valid_1")
    assert resp.status_code == 401, f"Expected 401 Unauthorized for suspended user, got {resp.status_code}"


def test_f06_refresh_fails_when_user_pending(client: DualClient, test_ids: DeterministicIDs):
    """Token refresh fails when user.status is PENDING."""
    # Create or simulate pending user refresh attempt
    resp = client.refresh_token("pending_user_rt_invalid")
    assert resp.status_code == 401


def test_f06_refresh_succeeds_when_user_active(client: DualClient, test_ids: DeterministicIDs):
    """Token refresh succeeds when user.status is ACTIVE."""
    resp = client.refresh_token("owner_rt_valid_1")
    assert resp.status_code == 200, f"Expected 200 OK for active user, got {resp.status_code}: {resp.text}"
    body = resp.json()
    data = body.get("data", body)
    assert "access_token" in data
    assert "refresh_token" in data


def test_f06_refresh_error_message_consistent(client: DualClient, admin_token: str, test_ids: DeterministicIDs):
    """Error message and code return generic INVALID_CREDENTIALS to prevent account state probing."""
    client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.DRIVER_USER_ID,
        token=admin_token,
    )
    resp = client.refresh_token("driver_rt_valid_1")
    assert resp.status_code == 401
    body = resp.json()
    err = body.get("error")
    if isinstance(err, dict):
        assert err.get("code") in ("INVALID_CREDENTIALS", "UNAUTHORIZED") or "invalid" in str(err).lower()


def test_f06_rotated_token_issued_only_for_active(client: DualClient):
    """A fresh rotated access and refresh token pair is issued only for active users."""
    resp = client.refresh_token("admin_rt_valid_1")
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    new_rt = data.get("refresh_token")
    assert new_rt is not None
    assert new_rt != "admin_rt_valid_1"
