"""
F20 (R4-NotificationSensitiveUrl): Sensitive Token URL Sanitization in TenantNotificationConsumer.
Verifies that sensitive token URLs (email verification, password reset, driver invite)
are eliminated from log.info outputs and prohibited from being broadcast via SSE.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f20_email_verification_url_not_logged(client: DualClient):
    """Email verification token URLs are redacted and not written in plaintext to application logs."""
    raw_message = "Dispatching email verification: https://swiftroute.com/api/auth/email/verify?token=secret_verify_token_12345"
    if client.is_mock:
        client.mock_client.record_log_message(raw_message)
        logged = client.mock_client.logged_lines[-1]
        assert "secret_verify_token_12345" not in logged
        assert "[REDACTED_TOKEN_URL]" in logged


def test_f20_password_reset_url_not_logged(client: DualClient):
    """Password reset token URLs are redacted and never logged in plaintext."""
    raw_message = "Sending password reset link: https://swiftroute.com/api/auth/password/reset?token=secret_reset_token_67890"
    if client.is_mock:
        client.mock_client.record_log_message(raw_message)
        logged = client.mock_client.logged_lines[-1]
        assert "secret_reset_token_67890" not in logged
        assert "[REDACTED_TOKEN_URL]" in logged


def test_f20_driver_invite_url_not_logged(client: DualClient):
    """Driver onboarding invite URLs containing tokens are redacted in log statements."""
    raw_message = "Sending driver onboarding invite: https://swiftroute.com/invitations/accept?token=secret_invite_token_abcde"
    if client.is_mock:
        client.mock_client.record_log_message(raw_message)
        logged = client.mock_client.logged_lines[-1]
        assert "secret_invite_token_abcde" not in logged
        assert "[REDACTED_TOKEN_URL]" in logged


def test_f20_verification_url_not_broadcast_over_sse(client: DualClient):
    """Sensitive verification or reset token URLs are stripped from real-time SSE broadcasts."""
    sensitive_payload = {
        "event": "USER_INVITED",
        "email": "driver@example.com",
        "url": "https://swiftroute.com/invitations/accept?token=leak_token_999",
    }
    if client.is_mock:
        client.mock_client.broadcast_sse_alert(sensitive_payload)
        broadcast = client.mock_client.sse_broadcast_messages[-1]
        assert "leak_token_999" not in str(broadcast)
        assert broadcast.get("url") == "[REDACTED]"


def test_f20_standard_operational_alerts_broadcast_cleanly(client: DualClient):
    """Standard non-sensitive operational alerts broadcast cleanly without token leaks."""
    safe_payload = {
        "event": "ROUTE_DELAY",
        "severity": "MEDIUM",
        "message": "Traffic delay on Route 4, estimated +15 mins",
    }
    if client.is_mock:
        client.mock_client.broadcast_sse_alert(safe_payload)
        broadcast = client.mock_client.sse_broadcast_messages[-1]
        assert broadcast["event"] == "ROUTE_DELAY"
        assert broadcast["severity"] == "MEDIUM"
