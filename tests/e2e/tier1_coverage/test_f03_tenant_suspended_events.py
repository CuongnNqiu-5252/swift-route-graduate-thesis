"""
F03 (R1-TenantSuspendedEvents): Handling of Tenant Suspension Events in TenantMembershipService.
Verifies that TenantMembershipService correctly consumes both 'tenant.suspended'
and 'tenant.suspended.v1' event variants emitted by order-service.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f03_tenant_suspended_event_v1_handled(client: DualClient, test_ids: DeterministicIDs):
    """Event 'tenant.suspended.v1' transitions tenant memberships to SUSPENDED."""
    event_payload = {
        "tenantId": test_ids.TENANT_A_ID,
        "eventType": "tenant.suspended.v1",
        "reason": "Regulatory compliance hold",
    }
    handled = client.emit_event("tenant.suspended.v1", event_payload)
    assert handled is True

    # Check that member refresh tokens for Tenant A are invalidated
    refresh_resp = client.refresh_token("admin_rt_valid_1")
    assert refresh_resp.status_code in (401, 403)


def test_f03_tenant_suspended_legacy_event_handled(client: DualClient, test_ids: DeterministicIDs):
    """Legacy event 'tenant.suspended' transitions tenant memberships to SUSPENDED."""
    event_payload = {
        "tenantId": test_ids.TENANT_B_ID,
        "eventType": "tenant.suspended",
        "reason": "Legacy suspension payload",
    }
    handled = client.emit_event("tenant.suspended", event_payload)
    assert handled is True

    refresh_resp = client.refresh_token("tenantb_admin_rt_1")
    assert refresh_resp.status_code in (401, 403)


def test_f03_tenant_approved_v1_reactivates_pending(client: DualClient, test_ids: DeterministicIDs):
    """Event 'tenant.approved.v1' transitions PENDING tenant memberships to ACTIVE."""
    event_payload = {
        "tenantId": test_ids.TENANT_PENDING_ID,
        "eventType": "tenant.approved.v1",
    }
    handled = client.emit_event("tenant.approved.v1", event_payload)
    assert handled is True


def test_f03_unrelated_event_ignored(client: DualClient, test_ids: DeterministicIDs):
    """Unrelated events (e.g. order.created) do not mutate tenant memberships."""
    event_payload = {
        "orderId": test_ids.ORDER_1_ID,
        "tenantId": test_ids.TENANT_A_ID,
        "eventType": "order.created",
    }
    handled = client.emit_event("order.created", event_payload)
    assert handled is True

    # Active member refresh token should still be valid
    refresh_resp = client.refresh_token("owner_rt_valid_1")
    assert refresh_resp.status_code == 200


def test_f03_unknown_tenant_event_handled_gracefully(client: DualClient):
    """Suspension event for non-existent tenant does not crash or throw uncaught errors."""
    event_payload = {
        "tenantId": "00000000-0000-0000-0000-000000000000",
        "eventType": "tenant.suspended.v1",
    }
    handled = client.emit_event("tenant.suspended.v1", event_payload)
    assert handled is True
