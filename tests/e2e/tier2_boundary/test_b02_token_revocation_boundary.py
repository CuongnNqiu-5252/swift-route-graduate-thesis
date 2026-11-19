"""
B02 (R1-TokenRevocation Boundary): Edge & Corner Case Testing for Token Revocation.
Verifies replay attack rejection, multi-device family revocation, reactivated user token freshness,
revocation idempotency, and malformed token string handling.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b02_token_replay_attack_rejection(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """Replaying a previously revoked refresh token must be rejected with 401."""
    # Suspend operator
    client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OPERATOR_USER_ID,
        token=admin_token,
    )
    # Replay revoked token
    resp = client.refresh_token("operator_rt_valid_1")
    assert resp.status_code == 401, f"Expected 401 Unauthorized for replayed token, got {resp.status_code}"


def test_b02_multi_device_family_revocation(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """When a member is suspended, all active refresh tokens for that user are revoked."""
    if client.is_mock:
        # Simulate secondary active refresh token on another device
        client.mock_client.users[test_ids.OPERATOR_USER_ID]["active_refresh_tokens"].add("operator_mobile_device_rt")

    # Suspend operator
    client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OPERATOR_USER_ID,
        token=admin_token,
    )

    # Both tokens must fail
    resp1 = client.refresh_token("operator_rt_valid_1")
    assert resp1.status_code == 401

    if client.is_mock:
        resp2 = client.refresh_token("operator_mobile_device_rt")
        assert resp2.status_code == 401


def test_b02_reactivated_user_token_freshness(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """Reactivating a user does not restore previously revoked refresh tokens."""
    # Suspend operator
    client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OPERATOR_USER_ID,
        token=admin_token,
    )

    # Reactivate operator in DB
    if client.is_mock:
        client.mock_client.users[test_ids.OPERATOR_USER_ID]["status"] = "ACTIVE"
        client.mock_client.users[test_ids.OPERATOR_USER_ID]["memberships"][test_ids.TENANT_A_ID] = "OPERATOR"

    # Previously revoked token must still be rejected
    resp = client.refresh_token("operator_rt_valid_1")
    assert resp.status_code == 401, "Old revoked token must not become valid after reactivation"


def test_b02_revocation_idempotency(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """Repeated suspension calls maintain token revocation idempotently."""
    for _ in range(2):
        client.suspend_member(
            tenant_id=test_ids.TENANT_A_ID,
            member_id=test_ids.DRIVER_USER_ID,
            token=admin_token,
        )
        resp = client.refresh_token("driver_rt_valid_1")
        assert resp.status_code == 401


def test_b02_malformed_token_handling(client: DualClient):
    """Calling refresh_token with empty, whitespace, or malformed string returns 401."""
    for malformed in ["", "   ", "not_a_valid_token_xyz!@#"]:
        resp = client.refresh_token(malformed)
        assert resp.status_code == 401, f"Expected 401 for '{malformed}', got {resp.status_code}"
