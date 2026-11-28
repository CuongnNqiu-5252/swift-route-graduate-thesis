"""
B07 (R4-AuthRefreshTokenTtl Boundary): Edge & Corner Case Testing for Token Expiration & TTL.
Verifies clock skew tolerance boundary, exact 15m expiration second boundary, negative/zero TTL rejection,
future nbf claim handling, and alg:none attack defense.
"""

import base64
from datetime import datetime, timezone
import json
import pytest
from jose import jwt

from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import JWT_SECRET, JWT_ALGORITHM, DeterministicIDs


def test_b07_clock_skew_tolerance_boundary(
    client: DualClient, test_ids: DeterministicIDs, token_factory
):
    """A token generated with minor clock skew (5 seconds in the past) is valid and accepted."""
    now = int(datetime.now(timezone.utc).timestamp())
    token = token_factory(
        user_id=test_ids.OWNER_USER_ID,
        tenant_id=test_ids.TENANT_A_ID,
        tenant_role="OWNER",
        iat=now - 5,
        expires_in=900,
    )
    resp = client.get(f"/api/tenants/{test_ids.TENANT_A_ID}", token=token)
    assert resp.status_code == 200, f"Expected 200 for 5s clock skew, got {resp.status_code}"


def test_b07_exact_15m_expiration_second_boundary(
    client: DualClient, test_ids: DeterministicIDs, token_factory
):
    """A token evaluated at exp - 1s is accepted; at exp + 1s is rejected."""
    now = int(datetime.now(timezone.utc).timestamp())

    # Valid token (1 second before expiration)
    valid_token = token_factory(
        user_id=test_ids.OWNER_USER_ID,
        tenant_id=test_ids.TENANT_A_ID,
        tenant_role="OWNER",
        iat=now - 899,
        expires_in=900,  # exp = now + 1
    )
    resp_valid = client.get(f"/api/tenants/{test_ids.TENANT_A_ID}", token=valid_token)
    assert resp_valid.status_code == 200, f"Expected 200 at exp - 1s, got {resp_valid.status_code}"

    # Expired token (1 second past expiration)
    expired_token = token_factory(
        user_id=test_ids.OWNER_USER_ID,
        tenant_id=test_ids.TENANT_A_ID,
        tenant_role="OWNER",
        iat=now - 901,
        expires_in=900,  # exp = now - 1
    )
    resp_expired = client.get(f"/api/tenants/{test_ids.TENANT_A_ID}", token=expired_token)
    assert resp_expired.status_code == 401, f"Expected 401 at exp + 1s, got {resp_expired.status_code}"


def test_b07_negative_zero_ttl_rejection(
    client: DualClient, test_ids: DeterministicIDs, token_factory
):
    """A token crafted with exp <= iat (negative or zero TTL) is immediately rejected with 401."""
    now = int(datetime.now(timezone.utc).timestamp())
    claims = {
        "sub": test_ids.OWNER_USER_ID,
        "system_role": "USER",
        "tenant_id": test_ids.TENANT_A_ID,
        "tenant_role": "OWNER",
        "iat": now,
        "exp": now - 10,  # expired before issued
    }
    zero_ttl_token = jwt.encode(claims, JWT_SECRET, algorithm=JWT_ALGORITHM)
    resp = client.get(f"/api/tenants/{test_ids.TENANT_A_ID}", token=zero_ttl_token)
    assert resp.status_code == 401, f"Expected 401 for negative TTL token, got {resp.status_code}"


def test_b07_future_nbf_claim(
    client: DualClient, test_ids: DeterministicIDs
):
    """A token with an nbf (not before) claim set in the future is rejected with 401."""
    now = int(datetime.now(timezone.utc).timestamp())
    claims = {
        "sub": test_ids.OWNER_USER_ID,
        "system_role": "USER",
        "tenant_id": test_ids.TENANT_A_ID,
        "tenant_role": "OWNER",
        "iat": now,
        "nbf": now + 300,  # Valid only 5 minutes in future
        "exp": now + 900,
    }
    future_nbf_token = jwt.encode(claims, JWT_SECRET, algorithm=JWT_ALGORITHM)
    resp = client.get(f"/api/tenants/{test_ids.TENANT_A_ID}", token=future_nbf_token)
    assert resp.status_code == 401, f"Expected 401 for future nbf claim, got {resp.status_code}"


def test_b07_alg_none_attack_rejection(
    client: DualClient, test_ids: DeterministicIDs
):
    """Tokens using the insecure 'alg': 'none' attack are rejected with 401 Unauthorized."""
    header = base64.urlsafe_b64encode(json.dumps({"alg": "none", "typ": "JWT"}).encode()).decode().rstrip("=")
    payload = base64.urlsafe_b64encode(json.dumps({
        "sub": test_ids.OWNER_USER_ID,
        "system_role": "USER",
        "tenant_id": test_ids.TENANT_A_ID,
        "tenant_role": "OWNER",
    }).encode()).decode().rstrip("=")
    alg_none_token = f"{header}.{payload}."

    resp = client.get(f"/api/tenants/{test_ids.TENANT_A_ID}", token=alg_none_token)
    assert resp.status_code == 401, f"Expected 401 for alg:none attack, got {resp.status_code}"
