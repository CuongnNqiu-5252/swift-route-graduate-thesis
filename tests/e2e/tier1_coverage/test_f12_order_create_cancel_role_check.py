"""
F12 (R2-OrderCreateCancelRoleCheck): Order Creation & Cancellation Role Enforcement.
Verifies that DRIVER role is strictly barred from creating (POST /api/orders)
or cancelling (PATCH /api/orders/{id}/cancel) orders, while OPERATOR and OWNER are permitted.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f12_driver_cannot_create_order(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER role attempting to create an order is rejected with 403."""
    order_data = {
        "recipient_name": "Unauthorized Order",
        "recipient_phone": "+84901234567",
        "delivery_address": "123 Delivery Road, District 1",
        "cod_amount": 50000.0,
    }
    resp = client.create_order(tenant_id=test_ids.TENANT_A_ID, order_data=order_data, token=driver_token)
    assert resp.status_code == 403, f"Expected 403 Forbidden for DRIVER order creation, got {resp.status_code}: {resp.text}"


def test_f12_driver_cannot_cancel_order(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER role attempting to cancel an order is rejected with 403."""
    resp = client.cancel_order(
        order_id=test_ids.ORDER_1_ID,
        reason="Driver unilateral cancellation",
        token=driver_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for DRIVER order cancellation, got {resp.status_code}: {resp.text}"


def test_f12_operator_can_create_order(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """OPERATOR role can successfully create orders (returns 201)."""
    order_data = {
        "recipient_name": "Valid Customer",
        "recipient_phone": "+84909999999",
        "delivery_address": "456 Logistic Blvd, District 7",
        "cod_amount": 100000.0,
    }
    resp = client.create_order(tenant_id=test_ids.TENANT_A_ID, order_data=order_data, token=operator_token)
    assert resp.status_code in (200, 201), f"Expected 200/201 for OPERATOR order creation, got {resp.status_code}: {resp.text}"


def test_f12_operator_can_cancel_order(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """OPERATOR role can successfully cancel orders (returns 200)."""
    resp = client.cancel_order(
        order_id=test_ids.ORDER_2_ID,
        reason="Customer requested cancellation via call center",
        token=operator_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 200, f"Expected 200 OK for OPERATOR order cancellation, got {resp.status_code}: {resp.text}"


def test_f12_owner_can_create_and_cancel_order(
    client: DualClient, owner_token: str, test_ids: DeterministicIDs
):
    """OWNER role can both create and cancel orders without restrictions."""
    create_resp = client.create_order(
        tenant_id=test_ids.TENANT_A_ID,
        order_data={"recipient_name": "VIP Customer", "cod_amount": 0.0},
        token=owner_token,
    )
    assert create_resp.status_code in (200, 201)
    new_order_id = create_resp.json().get("data", create_resp.json()).get("id")

    cancel_resp = client.cancel_order(
        order_id=new_order_id or test_ids.ORDER_1_ID,
        reason="Owner administrative cancellation",
        token=owner_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert cancel_resp.status_code == 200
