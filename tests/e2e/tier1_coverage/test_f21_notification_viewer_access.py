"""
F21 (R4-NotificationViewerAccess): Operational Alert & Inbox Access for VIEWER Role.
Verifies that VIEWER role is included in OPERATOR_ROLES for read access to
dispatch alerts, notification inbox, and SSE notification streaming.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f21_viewer_can_read_dispatch_alerts(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """VIEWER role can read operational dispatch alerts (200)."""
    resp = client.get_dispatch_alerts(token=viewer_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 200, f"Expected 200 OK for VIEWER alert read, got {resp.status_code}: {resp.text}"
    body = resp.json()
    alerts = body.get("data", body)
    assert isinstance(alerts, list)


def test_f21_viewer_can_read_notification_inbox(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """VIEWER role can read user notification inbox (200)."""
    resp = client.get_notification_inbox(token=viewer_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 200, f"Expected 200 OK for VIEWER inbox read, got {resp.status_code}: {resp.text}"


def test_f21_viewer_can_connect_notification_stream(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """VIEWER role can connect to the real-time notification SSE stream (200)."""
    resp = client.get_notification_stream(token=viewer_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 200, f"Expected 200 OK for VIEWER notification stream, got {resp.status_code}: {resp.text}"


def test_f21_unauthenticated_rejected_notification_read(client: DualClient):
    """Unauthenticated request to notification read endpoints returns 401 Unauthorized."""
    resp = client.get_dispatch_alerts(token=None)
    assert resp.status_code == 401, f"Expected 401 Unauthorized without token, got {resp.status_code}"


def test_f21_cross_tenant_viewer_rejected(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """VIEWER role in Tenant A attempting to read alerts of Tenant B is rejected with 403."""
    resp = client.get_dispatch_alerts(token=viewer_token, tenant_id=test_ids.TENANT_B_ID)
    assert resp.status_code == 403, f"Expected 403 Forbidden for cross-tenant alert read, got {resp.status_code}: {resp.text}"
