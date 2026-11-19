"""
F18 (R4-RoutingEngineInternalKey): Fail-Closed Internal Key Validation in routing-engine-service.
Verifies that an empty TENANT_INTERNAL_KEY on the server never allows authentication bypass
in api/tenant_security.py and service/grpc_server.py.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import ROUTING_INTERNAL_KEY


def test_f18_empty_internal_key_does_not_bypass_auth(client: DualClient):
    """When server internal key is empty, sending x-internal-key must not bypass auth (returns 401)."""
    # Temporarily emulate empty key on server
    if client.is_mock:
        client.mock_client.server_routing_internal_key = ""

    batch_payload = {"orders": [], "vehicles": []}
    resp = client.optimize_batch(batch_payload, internal_key="any_client_key")
    assert resp.status_code == 401, f"Expected 401 Unauthorized, got {resp.status_code}: {resp.text}"


def test_f18_configured_internal_key_authenticates(client: DualClient):
    """When server internal key is configured, providing matching key passes authentication (200)."""
    if client.is_mock:
        client.mock_client.server_routing_internal_key = ROUTING_INTERNAL_KEY

    batch_payload = {"orders": [], "vehicles": []}
    resp = client.optimize_batch(batch_payload, internal_key=ROUTING_INTERNAL_KEY)
    assert resp.status_code == 200, f"Expected 200 OK with valid internal key, got {resp.status_code}: {resp.text}"


def test_f18_mismatched_internal_key_rejected(client: DualClient):
    """Providing an incorrect internal key returns 401 Unauthorized."""
    if client.is_mock:
        client.mock_client.server_routing_internal_key = ROUTING_INTERNAL_KEY

    batch_payload = {"orders": [], "vehicles": []}
    resp = client.optimize_batch(batch_payload, internal_key="wrong_internal_key")
    assert resp.status_code == 401, f"Expected 401 Unauthorized for mismatched key, got {resp.status_code}: {resp.text}"


def test_f18_grpc_internal_key_empty_rejected(client: DualClient):
    """gRPC service fails closed when server internal key is unconfigured or empty."""
    if client.is_mock:
        client.mock_client.server_routing_internal_key = "   "

    resp = client.optimize_batch({}, internal_key="")
    assert resp.status_code == 401, f"Expected 401 for whitespace/empty key, got {resp.status_code}"


def test_f18_grpc_missing_metadata_rejected(client: DualClient):
    """Requests lacking authentication header and internal key return 401."""
    resp = client.optimize_batch({}, token=None, internal_key=None)
    assert resp.status_code == 401, f"Expected 401 for missing credentials, got {resp.status_code}"
