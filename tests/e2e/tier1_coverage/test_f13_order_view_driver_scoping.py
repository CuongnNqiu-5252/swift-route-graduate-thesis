"""
F13 (R2-OrderViewDriverScoping): Driver Scoping in OrderService.getOrderById.
Verifies that DRIVER can only view orders assigned to them,
blocking unassigned, peer-driver, and cross-tenant order inspection.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f13_driver_can_view_assigned_order(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER can view details of an order assigned to them."""
    resp = client.get_order(
        order_id=test_ids.ORDER_1_ID,  # Assigned to DRIVER_USER_ID
        token=driver_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 200, f"Expected 200 OK for assigned driver, got {resp.status_code}: {resp.text}"
    order = resp.json().get("data", resp.json())
    assert order.get("id") == test_ids.ORDER_1_ID


def test_f13_driver_cannot_view_unassigned_order(
    client: DualClient, driver_token: str, admin_token: str, test_ids: DeterministicIDs
):
    """DRIVER cannot view unassigned orders in the tenant (rejected with 403)."""
    # Create an unassigned order
    create_resp = client.create_order(
        tenant_id=test_ids.TENANT_A_ID,
        order_data={"recipient_name": "Unassigned Recipient", "cod_amount": 100.0},
        token=admin_token,
    )
    unassigned_order_id = create_resp.json().get("data", create_resp.json()).get("id")

    resp = client.get_order(
        order_id=unassigned_order_id,
        token=driver_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"


def test_f13_driver_cannot_view_peer_driver_order(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER cannot view an order assigned to another driver (rejected with 403)."""
    resp = client.get_order(
        order_id=test_ids.ORDER_2_ID,  # Assigned to DRIVER_2_USER_ID
        token=driver_token,             # Called by DRIVER_USER_ID
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"


def test_f13_operator_can_view_any_tenant_order(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """OPERATOR can view any order within their tenant regardless of assignment."""
    resp1 = client.get_order(order_id=test_ids.ORDER_1_ID, token=operator_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp1.status_code == 200

    resp2 = client.get_order(order_id=test_ids.ORDER_2_ID, token=operator_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp2.status_code == 200


def test_f13_cross_tenant_order_view_blocked(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """A driver attempting to view an order in another tenant is rejected with 403/404."""
    resp = client.get_order(
        order_id=test_ids.ORDER_TENANT_B_ID,
        token=driver_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code in (403, 404), f"Expected 403/404, got {resp.status_code}: {resp.text}"
