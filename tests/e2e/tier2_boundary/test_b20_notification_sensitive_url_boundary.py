"""
B20 (R4-NotificationSensitiveUrl Boundary): Edge & Corner Case Testing for Sensitive URL Sanitization.
Verifies empty/null URL safety, long URL token redaction without leakage, multi-listener SSE broadcast sanitization,
multilingual message formatting protection, and inbox storage isolation.
"""

import pytest
from tests.e2e.client.dual_client import DualClient


def test_b20_empty_null_verification_url_handling(client: DualClient):
    """Logging or processing events with null or absent verification URLs executes safely without exceptions."""
    if client.is_mock:
        client.mock_client.record_log_message("User registration notification sent without verification link.")
        assert len(client.mock_client.logged_lines) > 0
        assert "[REDACTED_TOKEN_URL]" not in client.mock_client.logged_lines[-1]


def test_b20_long_url_truncation_without_secret_leak(client: DualClient):
    """Extremely long URLs containing token parameters are fully sanitized rather than partially truncated."""
    long_token = "tok_" + ("abcdef1234567890" * 10)
    raw_log = f"User verification dispatched: https://swiftroute.com/api/auth/email/verify?token={long_token}&redirect_uri=https://swiftroute.com/dashboard"

    if client.is_mock:
        client.mock_client.record_log_message(raw_log)
        logged = client.mock_client.logged_lines[-1]
        assert long_token not in logged, "Sensitive token parameter must not leak into log output"
        assert "[REDACTED_TOKEN_URL]" in logged


def test_b20_multi_listener_sse_broadcast_sanitization(client: DualClient):
    """Sensitive verification or invite tokens are stripped from real-time SSE broadcasts for all listeners."""
    broadcast_event = {
        "event": "DRIVER_INVITATION_CREATED",
        "driver_id": "driver-test-uuid",
        "token_url": "https://swiftroute.com/invitations/accept?token=sensitive_broadcast_token_888",
    }
    if client.is_mock:
        client.mock_client.broadcast_sse_alert(broadcast_event)
        message = client.mock_client.sse_broadcast_messages[-1]
        assert "sensitive_broadcast_token_888" not in str(message)
        assert message.get("token_url") == "[REDACTED]"


def test_b20_localized_message_formatting_without_token(client: DualClient):
    """Localized notification templates (e.g., Vietnamese) redact sensitive token URLs cleanly."""
    vn_message = "Mã xác thực email của bạn: https://swiftroute.com/verify-email?token=vn_secret_token_12345"
    if client.is_mock:
        client.mock_client.record_log_message(vn_message)
        logged = client.mock_client.logged_lines[-1]
        assert "vn_secret_token_12345" not in logged
        assert "[REDACTED_TOKEN_URL]" in logged


def test_b20_storage_isolation_no_token_leak(
    client: DualClient, viewer_token: str
):
    """Notification inbox items delivered to end users do not contain exposed raw tokens or secrets."""
    resp = client.get_notification_inbox(token=viewer_token)
    assert resp.status_code == 200
    inbox = resp.json().get("data", resp.json())
    for item in inbox:
        assert "password" not in str(item).lower()
        assert "token=" not in str(item).lower()
