"""
B18 (R4-RoutingEngineInternalKey Boundary): Edge & Corner Case Testing for Routing Internal Key.
Verifies whitespace-only key fail-closed defense, empty metadata rejection, special character resistance,
exact authentication success with configured key, and missing credentials rejection.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import ROUTING_INTERNAL_KEY


def test_b18_whitespace_only_internal_key_treated_as_unset(client: DualClient):
    """When server internal key is whitespace-only, client internal key authentication is disabled (fails closed with 401)."""
    if client.is_mock:
        client.mock_client.server_routing_internal_key = "   "

    resp = client.optimize_batch(
        batch_data={"orders": []},
        internal_key="any_client_attempted_key",
    )
    assert resp.status_code == 401, f"Expected 401 for unset server key, got {resp.status_code}"


def test_b18_grpc_empty_string_metadata(client: DualClient):
    """Providing an empty string as internal key returns 401 Unauthorized (gRPC code 16 equivalent)."""
    if client.is_mock:
        client.mock_client.server_routing_internal_key = ROUTING_INTERNAL_KEY

    resp = client.optimize_batch(
        batch_data={"orders": []},
        internal_key="",
    )
    assert resp.status_code == 401, f"Expected 401 for empty key, got {resp.status_code}"


def test_b18_special_characters_in_key(client: DualClient):
    """Internal key containing control characters, newlines, or injection strings returns 401."""
    if client.is_mock:
        client.mock_client.server_routing_internal_key = ROUTING_INTERNAL_KEY

    malicious_keys = [
        "key\x00nullbyte",
        "key\nnewline",
        "' OR '1'='1",
        "key; rm -rf /",
    ]
    for key in malicious_keys:
        resp = client.optimize_batch(
            batch_data={"orders": []},
            internal_key=key,
        )
        assert resp.status_code == 401, f"Expected 401 for malicious key '{key}', got {resp.status_code}"


def test_b18_configured_internal_key_authenticates(client: DualClient):
    """When server internal key is validly configured, matching client key authenticates with 200 OK."""
    if client.is_mock:
        client.mock_client.server_routing_internal_key = ROUTING_INTERNAL_KEY

    resp = client.optimize_batch(
        batch_data={"orders": []},
        internal_key=ROUTING_INTERNAL_KEY,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}"
    data = resp.json().get("data", resp.json())
    assert data.get("algorithm_used") == "dp_ml_pruning"


def test_b18_missing_credentials_rejected(client: DualClient):
    """Optimization request lacking both JWT bearer token and internal key is rejected with 401."""
    resp = client.optimize_batch(
        batch_data={"orders": []},
        token=None,
        internal_key=None,
    )
    assert resp.status_code == 401, f"Expected 401 for missing credentials, got {resp.status_code}"
