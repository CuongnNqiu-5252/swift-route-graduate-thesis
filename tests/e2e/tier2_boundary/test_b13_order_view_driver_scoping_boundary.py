"""
B13 (R2-OrderViewDriverScoping Boundary): Edge & Corner Case Testing for Driver Order Scoping.
Verifies immediate access revocation post-reassignment, malformed/nonexistent order UUID handling,
unassigned order view prevention, assigned order read permission, and SYSTEM_ADMIN unrestricted access.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b13_view_order_after_reassignment_revoked(
    client: DualClient, driver_token: str, driver_2_token: str, test_ids: DeterministicIDs
):
    """When an order is reassigned from DRIVER 1 to DRIVER 2, DRIVER 1 immediately loses view access (403)."""
    # Initially ORDER_1 is assigned to DRIVER 1
    resp_init = client.get_order(test_ids.ORDER_1_ID, token=driver_token)
    assert resp_init.status_code == 200

    # Reassign order to DRIVER 2
    if client.is_mock:
        client.mock_client.orders[test_ids.ORDER_1_ID]["assigned_driver_id"] = test_ids.DRIVER_2_USER_ID

    # DRIVER 1 attempt must now be rejected
    resp_revoked = client.get_order(test_ids.ORDER_1_ID, token=driver_token)
    assert resp_revoked.status_code == 403, f"Expected 403 after reassignment, got {resp_revoked.status_code}"

    # DRIVER 2 can now view it
    resp_new = client.get_order(test_ids.ORDER_1_ID, token=driver_2_token)
    assert resp_new.status_code == 200


def test_b13_malformed_or_nonexistent_order_uuid(
    client: DualClient, operator_token: str
):
    """Retrieving a non-existent or malformed order UUID returns 404 Not Found."""
    resp = client.get_order("00000000-0000-0000-0000-000000000000", token=operator_token)
    assert resp.status_code == 404, f"Expected 404, got {resp.status_code}"


def test_b13_driver_cannot_view_unassigned_pending_order(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER 1 cannot view ORDER_2 which is assigned to DRIVER 2 (fails with 403)."""
    resp = client.get_order(test_ids.ORDER_2_ID, token=driver_token)
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}"


def test_b13_driver_view_assigned_order_allowed(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER 1 can view ORDER_1 which is assigned to them (succeeds with 200 OK)."""
    resp = client.get_order(test_ids.ORDER_1_ID, token=driver_token)
    assert resp.status_code == 200
    data = resp.json().get("data", resp.json())
    assert data.get("assigned_driver_id") == test_ids.DRIVER_USER_ID


def test_b13_system_admin_unrestricted_view(
    client: DualClient, system_admin_token: str, test_ids: DeterministicIDs
):
    """SYSTEM_ADMIN has unrestricted read access across all orders regardless of assignment or tenant."""
    for order_id in [test_ids.ORDER_1_ID, test_ids.ORDER_2_ID, test_ids.ORDER_TENANT_B_ID]:
        resp = client.get_order(order_id, token=system_admin_token)
        assert resp.status_code == 200, f"Expected 200 for SYSTEM_ADMIN on {order_id}, got {resp.status_code}"
