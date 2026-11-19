"""
F17 (R4-DriverServiceInternalKey): Service-to-Service Key Hardening in driver-service.
Verifies that internal calls via X-Service-Key with tenantId == null do not dump
all drivers across all tenants, and empty/invalid service keys fail closed.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DRIVER_SERVICE_KEY, DeterministicIDs


def test_f17_internal_key_without_tenant_id_rejected(client: DualClient):
    """Internal service key call without a tenant_id query parameter is rejected (403/400)."""
    resp = client.get_all_drivers_internal(service_key=DRIVER_SERVICE_KEY, tenant_id=None)
    assert resp.status_code in (400, 403), f"Expected 400/403 for missing tenant_id dump, got {resp.status_code}: {resp.text}"


def test_f17_internal_key_with_tenant_id_returns_tenant_drivers(
    client: DualClient, test_ids: DeterministicIDs
):
    """Internal service key call specifying tenant_id returns only drivers belonging to that tenant."""
    resp = client.get_all_drivers_internal(service_key=DRIVER_SERVICE_KEY, tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}: {resp.text}"
    drivers = resp.json().get("data", resp.json())
    assert isinstance(drivers, list)
    for d in drivers:
        assert d.get("tenant_id") == test_ids.TENANT_A_ID


def test_f17_invalid_internal_key_rejected(client: DualClient, test_ids: DeterministicIDs):
    """Providing an invalid X-Service-Key is rejected with 401 or 403."""
    resp = client.get_all_drivers_internal(service_key="invalid_forged_key", tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code in (401, 403), f"Expected 401/403, got {resp.status_code}: {resp.text}"


def test_f17_empty_internal_key_does_not_bypass(client: DualClient, test_ids: DeterministicIDs):
    """An empty string X-Service-Key does not bypass authentication (rejected with 401/403)."""
    resp = client.get_all_drivers_internal(service_key="", tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code in (401, 403), f"Expected 401/403, got {resp.status_code}: {resp.text}"


def test_f17_system_admin_can_query_without_tenant(
    client: DualClient, system_admin_token: str, test_ids: DeterministicIDs
):
    """SYSTEM_ADMIN can perform administrative driver queries across tenants."""
    resp = client.get("/api/drivers", token=system_admin_token)
    assert resp.status_code in (200, 404), f"SYSTEM_ADMIN should not receive 403, got {resp.status_code}"
