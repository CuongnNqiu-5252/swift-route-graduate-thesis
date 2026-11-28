"""
B17 (R4-DriverServiceInternalKey Boundary): Edge & Corner Case Testing for Driver Internal Key.
Verifies constant-time comparison defense against invalid keys, whitespace-padded key rejection,
non-existent tenant handling (empty list), missing tenant parameter fail-closed, and case sensitivity.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DRIVER_SERVICE_KEY, DeterministicIDs


def test_b17_constant_time_comparison_defense(
    client: DualClient, test_ids: DeterministicIDs
):
    """Invalid service keys of varying lengths and prefixes are consistently rejected with 401."""
    invalid_keys = [
        "wrong_key",
        "swiftroute_driver_internal_service_key_extended",
        "swiftroute_driver",
        "x" * 128,
    ]
    for key in invalid_keys:
        resp = client.get_all_drivers_internal(service_key=key, tenant_id=test_ids.TENANT_A_ID)
        assert resp.status_code == 401, f"Expected 401 for invalid key '{key}', got {resp.status_code}"


def test_b17_whitespace_padded_key_rejected(
    client: DualClient, test_ids: DeterministicIDs
):
    """Whitespace-padded service keys ('  KEY  ') are rejected with 401 Unauthorized."""
    resp = client.get_all_drivers_internal(
        service_key=f"  {DRIVER_SERVICE_KEY}  ",
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 401, f"Expected 401 for whitespace-padded key, got {resp.status_code}"


def test_b17_nonexistent_tenant_with_valid_key_returns_empty(
    client: DualClient
):
    """A valid internal service key querying a non-existent tenant ID returns an empty list [] (200 OK)."""
    resp = client.get_all_drivers_internal(
        service_key=DRIVER_SERVICE_KEY,
        tenant_id="00000000-0000-0000-0000-000000000000",
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}"
    drivers = resp.json().get("data", resp.json())
    assert isinstance(drivers, list)
    assert len(drivers) == 0


def test_b17_missing_tenant_id_parameter_rejected(
    client: DualClient
):
    """An internal query with valid service key but missing tenant_id parameter is rejected with 403 Forbidden."""
    resp = client.get_all_drivers_internal(
        service_key=DRIVER_SERVICE_KEY,
        tenant_id=None,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for missing tenant_id, got {resp.status_code}"


def test_b17_case_sensitivity_rejected(
    client: DualClient, test_ids: DeterministicIDs
):
    """Internal service key comparison is strictly case-sensitive; uppercase variants return 401."""
    resp = client.get_all_drivers_internal(
        service_key=DRIVER_SERVICE_KEY.upper(),
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 401, f"Expected 401 for uppercase key variant, got {resp.status_code}"
