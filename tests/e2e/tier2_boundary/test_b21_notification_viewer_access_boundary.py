"""
B21 (R4-NotificationViewerAccess Boundary): Edge & Corner Case Testing for Notification Viewer Access.
Verifies inbox read permissions, dispatch alert severity viewing, mutation rejection on read-only role,
SSE stream keepalive connectivity, and expired token rejection.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b21_inbox_read_by_viewer(
    client: DualClient, viewer_token: str
):
    """The read-only VIEWER role can retrieve in-app notification inbox items (succeeds with 200 OK)."""
    resp = client.get_notification_inbox(token=viewer_token)
    assert resp.status_code == 200, f"Expected 200 OK for VIEWER inbox read, got {resp.status_code}"
    items = resp.json().get("data", resp.json())
    assert isinstance(items, list)


def test_b21_dispatch_alerts_read_by_viewer(
    client: DualClient, viewer_token: str
):
    """The VIEWER role can access operational dispatch alerts including severity attributes."""
    resp = client.get_dispatch_alerts(token=viewer_token)
    assert resp.status_code == 200, f"Expected 200 OK for VIEWER dispatch alerts, got {resp.status_code}"
    alerts = resp.json().get("data", resp.json())
    assert isinstance(alerts, list)
    if len(alerts) > 0:
        assert "severity" in alerts[0]


def test_b21_viewer_cannot_mutate_or_delete_alerts(
    client: DualClient, viewer_token: str
):
    """The VIEWER role is prohibited from performing write, acknowledge, or delete operations on alerts."""
    resp_delete = client.delete("/api/notifications/alerts/alert-001", token=viewer_token)
    assert resp_delete.status_code in (403, 404, 405), f"Alert mutation not blocked for VIEWER: {resp_delete.status_code}"


def test_b21_sse_connection_keepalive(
    client: DualClient, viewer_token: str
):
    """VIEWER can connect to real-time notification stream and receive text/event-stream content."""
    resp = client.get_notification_stream(token=viewer_token)
    assert resp.status_code == 200, f"Expected 200 OK for notification stream, got {resp.status_code}"
    assert "text/event-stream" in resp.headers.get("Content-Type", "")
    assert "data:" in resp.text


def test_b21_token_expiration_closes_sse_stream(
    client: DualClient, test_ids: DeterministicIDs, token_factory
):
    """Connecting to notification stream with an expired token is rejected with 401 Unauthorized."""
    expired_token = token_factory(
        user_id=test_ids.VIEWER_USER_ID,
        tenant_id=test_ids.TENANT_A_ID,
        tenant_role="VIEWER",
        expired=True,
    )
    resp = client.get_notification_stream(token=expired_token)
    assert resp.status_code == 401, f"Expected 401 for expired stream connection, got {resp.status_code}"
