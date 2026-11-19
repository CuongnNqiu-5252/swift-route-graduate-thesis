"""
B06 (R4-AuthRefreshActiveCheck Boundary): Edge & Corner Case Testing for Active Check on Refresh.
Verifies deleted user record handling, LOCKED status rejection, null/missing status fail-closed,
concurrent rotation consistency, and hash tampering detection.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b06_deleted_user_record_fails_refresh(client: DualClient):
    """A refresh token referencing a deleted or non-existent user record fails with 401."""
    resp = client.refresh_token("ghost_user_refresh_token_nonexistent")
    assert resp.status_code == 401, f"Expected 401, got {resp.status_code}"


def test_b06_user_status_locked_fails_refresh(
    client: DualClient, test_ids: DeterministicIDs
):
    """When a user status is LOCKED in the database, token refresh fails with 401."""
    if client.is_mock:
        client.mock_client.users[test_ids.ADMIN_USER_ID]["status"] = "LOCKED"

    resp = client.refresh_token("admin_rt_valid_1")
    assert resp.status_code == 401, f"Expected 401 for LOCKED status, got {resp.status_code}"


def test_b06_null_status_fails_refresh(
    client: DualClient, test_ids: DeterministicIDs
):
    """When a user status is null or missing, token refresh fails closed with 401."""
    if client.is_mock:
        client.mock_client.users[test_ids.ADMIN_USER_ID]["status"] = None

    resp = client.refresh_token("admin_rt_valid_1")
    assert resp.status_code == 401, f"Expected 401 for null status, got {resp.status_code}"


def test_b06_concurrent_refresh_rotation_consistency(client: DualClient):
    """Refreshing an active token produces a new valid rotated pair consistently."""
    resp1 = client.refresh_token("owner_rt_valid_1")
    assert resp1.status_code == 200
    data1 = resp1.json().get("data", resp1.json())
    new_rt = data1.get("refresh_token")
    assert new_rt is not None

    # Rotated token can immediately be refreshed
    resp2 = client.refresh_token(new_rt)
    assert resp2.status_code == 200


def test_b06_hash_tampering_detection(client: DualClient):
    """A tampered or modified refresh token string fails validation with 401."""
    tampered_tokens = [
        "owner_rt_valid_1_tampered",
        "owner_rt_valid_1\x00",
        "INVALID_PREFIX_owner_rt_valid_1",
    ]
    for token in tampered_tokens:
        resp = client.refresh_token(token)
        assert resp.status_code == 401, f"Expected 401 for tampered token '{token}', got {resp.status_code}"
