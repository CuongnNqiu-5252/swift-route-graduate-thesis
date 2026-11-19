"""
F07 (R4-AuthRefreshTokenTtl): Access Token Expiration Verification.
Verifies that newly issued access tokens have an expiration of exactly 15 minutes (900 seconds),
rejecting the previously hardcoded 60-minute duration.
"""

from datetime import datetime, timezone
from jose import jwt
import pytest

from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import JWT_SECRET, JWT_ALGORITHM, DeterministicIDs


def test_f07_access_token_ttl_matches_configured_15_mins(client: DualClient):
    """Issued access token TTL (exp - iat) is configured to 15 minutes (900s)."""
    resp = client.refresh_token("owner_rt_valid_1")
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    token = data["access_token"]
    claims = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])

    ttl = claims["exp"] - claims["iat"]
    assert ttl == 900, f"Expected 900 seconds (15 minutes), got {ttl}"


def test_f07_access_token_not_hardcoded_60_mins(client: DualClient):
    """Issued access token TTL is not hardcoded to 60 minutes (3600s)."""
    resp = client.refresh_token("admin_rt_valid_1")
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    token = data["access_token"]
    claims = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])

    ttl = claims["exp"] - claims["iat"]
    assert ttl != 3600, "Access token expiration must not be hardcoded to 60 minutes"


def test_f07_access_token_accepted_within_14_mins(client: DualClient, test_ids: DeterministicIDs, token_factory):
    """A token generated 14 minutes ago (within 15-minute window) is accepted."""
    now = int(datetime.now(timezone.utc).timestamp())
    # iat = 14 mins ago, total lifespan = 15 mins (exp = now + 60s)
    token = token_factory(
        user_id=test_ids.OWNER_USER_ID,
        tenant_id=test_ids.TENANT_A_ID,
        tenant_role="OWNER",
        iat=now - 840,
        expires_in=900,
    )
    resp = client.get(f"/api/tenants/{test_ids.TENANT_A_ID}", token=token)
    assert resp.status_code == 200, f"Expected 200 OK within 15 mins window, got {resp.status_code}"


def test_f07_access_token_rejected_after_15_mins(client: DualClient, test_ids: DeterministicIDs, token_factory):
    """A token older than 15 minutes (expired) is rejected by Gateway / Service with 401."""
    now = int(datetime.now(timezone.utc).timestamp())
    # iat = 20 mins ago, exp = 5 mins ago
    token = token_factory(
        user_id=test_ids.OWNER_USER_ID,
        tenant_id=test_ids.TENANT_A_ID,
        tenant_role="OWNER",
        iat=now - 1200,
        expired=True,
    )
    resp = client.get(f"/api/tenants/{test_ids.TENANT_A_ID}", token=token)
    assert resp.status_code == 401, f"Expected 401 Unauthorized for expired token, got {resp.status_code}"


def test_f07_refresh_token_ttl_preserved(client: DualClient):
    """Refresh token expires_in field reflects the access token duration."""
    resp = client.refresh_token("owner_rt_valid_1")
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    assert data.get("expires_in") == 900
