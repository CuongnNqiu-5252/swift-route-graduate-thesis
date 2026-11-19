"""
F16 (R4-SettlementPermissions): Settlement Governance & Viewer Read Permissions in driver-service.
Verifies that OPERATOR cannot approve or mark paid driver settlements (restricted to OWNER, TENANT_ADMIN,
and SYSTEM_ADMIN), while VIEWER is granted access to operational read endpoints.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f16_operator_cannot_approve_settlement(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """OPERATOR role cannot approve driver settlements (rejected with 403)."""
    resp = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=operator_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for OPERATOR approval, got {resp.status_code}: {resp.text}"


def test_f16_operator_cannot_mark_paid_settlement(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """OPERATOR role cannot mark driver settlements as paid (rejected with 403)."""
    payout_data = {"payment_reference": "TX-123456", "payment_method": "BANK_TRANSFER"}
    resp = client.mark_settlement_paid(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        payout_data=payout_data,
        token=operator_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for OPERATOR payout, got {resp.status_code}: {resp.text}"


def test_f16_tenant_admin_can_approve_settlement(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """TENANT_ADMIN can approve driver settlements (200)."""
    resp = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=admin_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 200, f"Expected 200 OK for TENANT_ADMIN approval, got {resp.status_code}: {resp.text}"
    body = resp.json()
    data = body.get("data", body)
    assert data.get("status") == "APPROVED"


def test_f16_owner_can_mark_paid_settlement(
    client: DualClient, owner_token: str, test_ids: DeterministicIDs
):
    """OWNER can mark driver settlements as paid (200)."""
    payout_data = {"payment_reference": "TX-PAY-9999", "payment_method": "BANK_TRANSFER"}
    resp = client.mark_settlement_paid(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        payout_data=payout_data,
        token=owner_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 200, f"Expected 200 OK for OWNER payout confirmation, got {resp.status_code}: {resp.text}"
    body = resp.json()
    data = body.get("data", body)
    assert data.get("status") == "PAID"


def test_f16_viewer_can_list_settlements_and_earnings(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """VIEWER role can read settlements and driver earnings read-only (200)."""
    settlements_resp = client.list_settlements(token=viewer_token, tenant_id=test_ids.TENANT_A_ID)
    assert settlements_resp.status_code == 200, f"Expected 200 for VIEWER settlement list, got {settlements_resp.status_code}"

    earnings_resp = client.get_earnings(token=viewer_token, tenant_id=test_ids.TENANT_A_ID)
    assert earnings_resp.status_code == 200, f"Expected 200 for VIEWER earnings read, got {earnings_resp.status_code}"
