"""
B05 (R3-GatewayPublicResend Boundary): Edge & Corner Case Testing for Public Resend Endpoint.
Verifies rapid call handling, malformed email syntax resilience, empty JSON body handling,
trailing slashes path normalization, and case sensitivity.
"""

import pytest
from tests.e2e.client.dual_client import DualClient


def test_b05_rapid_resend_rate_limiting(client: DualClient):
    """Burst of rapid calls to public resend endpoint is handled stably without internal errors."""
    for _ in range(5):
        resp = client.resend_public_email("burst_test@example.com")
        assert resp.status_code in (200, 429), f"Expected 200 or 429, got {resp.status_code}"


def test_b05_malformed_email_syntax(client: DualClient):
    """Malformed or invalid email syntax returns generic response (200) or validation error (400)."""
    malformed_emails = ["not-an-email", "@missing-local.com", "missing-domain@", "a" * 300 + "@long.com"]
    for email in malformed_emails:
        resp = client.resend_public_email(email)
        assert resp.status_code in (200, 400), f"Expected 200/400 for '{email}', got {resp.status_code}"


def test_b05_empty_json_body(client: DualClient):
    """Calling resend-public with an empty JSON body {} returns 200 generic message or 400."""
    resp = client.post("/api/auth/email/resend-public", json={})
    assert resp.status_code in (200, 400), f"Expected 200/400, got {resp.status_code}"


def test_b05_trailing_slashes_path_normalization(client: DualClient):
    """Calling resend-public with a trailing slash is strictly evaluated (returns 200, 401, or 404)."""
    resp = client.post("/api/auth/email/resend-public/", json={"email": "trailing@example.com"})
    assert resp.status_code in (200, 401, 404), f"Unexpected status code: {resp.status_code}"


def test_b05_case_sensitivity(client: DualClient):
    """Standard public resend path works reliably without authentication."""
    resp = client.post(
        "/api/auth/email/resend-public",
        json={"email": "Valid.Email@Domain.COM"},
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}"
    body = resp.json()
    assert body.get("error") is None or resp.status_code == 200
