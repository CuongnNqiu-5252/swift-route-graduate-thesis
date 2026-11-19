"""
B16 (R4-SettlementPermissions Boundary): Edge & Corner Case Testing for Settlement Permissions.
Verifies idempotent approval on already-approved settlements, owner payout execution,
non-existent settlement handling, VIEWER read permissions, and driver self-approval rejection.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b16_approve_already_approved_settlement(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """Approving an already-approved settlement executes idempotently and returns 200."""
    for _ in range(2):
        resp = client.approve_settlement(test_ids.SETTLEMENT_1_ID, token=admin_token)
        assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
        data = resp.json().get("data", resp.json())
        assert data.get("status") == "APPROVED"


def test_b16_mark_paid_by_owner_succeeds(
    client: DualClient, owner_token: str, test_ids: DeterministicIDs
):
    """OWNER can mark an approved settlement as PAID with payout transaction details."""
    payout_data = {
        "payment_reference": "VNPAY-REF-998877",
        "payout_method": "BANK_TRANSFER",
        "notes": "Weekly driver disbursement",
    }
    resp = client.mark_settlement_paid(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        payout_data=payout_data,
        token=owner_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}"
    data = resp.json().get("data", resp.json())
    assert data.get("status") == "PAID"


def test_b16_nonexistent_settlement_returns_404(
    client: DualClient, admin_token: str
):
    """Attempting to approve a non-existent settlement returns 404 Not Found."""
    resp = client.approve_settlement("00000000-0000-0000-0000-000000000000", token=admin_token)
    assert resp.status_code == 404, f"Expected 404, got {resp.status_code}"


def test_b16_viewer_paginated_read_allowed(
    client: DualClient, viewer_token: str
):
    """VIEWER role can inspect settlements lists and driver earnings summaries (200 OK)."""
    resp_settlements = client.list_settlements(token=viewer_token)
    assert resp_settlements.status_code == 200, f"Expected 200 for VIEWER settlements read, got {resp_settlements.status_code}"

    resp_earnings = client.get_earnings(token=viewer_token)
    assert resp_earnings.status_code == 200, f"Expected 200 for VIEWER earnings read, got {resp_earnings.status_code}"
    data = resp_earnings.json().get("data", resp_earnings.json())
    assert "total_earnings" in data


def test_b16_driver_self_approval_rejected(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER role attempting to approve their own settlement is rejected with 403 Forbidden."""
    resp = client.approve_settlement(test_ids.SETTLEMENT_1_ID, token=driver_token)
    assert resp.status_code == 403, f"Expected 403 Forbidden for driver self-approval, got {resp.status_code}"
