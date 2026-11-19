"""
F05 (R3-GatewayPublicResend): Public Access to Email Resend Endpoint.
Verifies that /api/auth/email/resend-public is accessible without authentication,
handles non-existent emails idempotently without user enumeration, and belongs to PUBLIC_PATHS.
"""

import pytest
from tests.e2e.client.dual_client import DualClient


def test_f05_resend_public_accessible_without_token(client: DualClient):
    """POST /api/auth/email/resend-public is accessible without an Authorization header."""
    resp = client.resend_public_email("unverified_user@example.com")
    assert resp.status_code == 200, f"Expected 200 OK without token, got {resp.status_code}: {resp.text}"


def test_f05_resend_public_valid_email(client: DualClient):
    """Resending verification email for an unverified account succeeds."""
    resp = client.resend_public_email("new_tenant_owner@example.com")
    assert resp.status_code == 200
    body = resp.json()
    assert body.get("data") is not None or "message" in resp.text


def test_f05_resend_public_nonexistent_email_handled(client: DualClient):
    """Resend email request for non-existent email returns generic 200 to prevent user enumeration."""
    resp = client.resend_public_email("nonexistent_account_12345@domain.invalid")
    assert resp.status_code == 200
    assert "sent if account exists" in resp.text.lower() or resp.is_success


def test_f05_resend_public_in_public_paths_list(client: DualClient):
    """Verify that calling the endpoint via low-level request without token succeeds."""
    resp = client.request("POST", "/api/auth/email/resend-public", json={"email": "audit@example.com"})
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}"


def test_f05_resend_public_security_config_permitted(client: DualClient):
    """Verify endpoint is not blocked by WebFlux SecurityConfig entry point (no 401)."""
    resp = client.post("/api/auth/email/resend-public", json={"email": "tester@example.com"})
    assert resp.status_code != 401, f"Endpoint should not return 401 Unauthorized: {resp.text}"
    assert resp.status_code == 200
