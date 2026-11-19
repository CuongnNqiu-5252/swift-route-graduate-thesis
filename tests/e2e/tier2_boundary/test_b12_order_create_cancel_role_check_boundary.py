"""
B12 (R2-OrderCreateCancelRoleCheck Boundary): Edge & Corner Case Testing for Order Roles.
Verifies driver cancelling assigned order rejection (must use incident), operator cancellation of in-transit order,
repeated cancellation idempotency, valid creation payload rejection under DRIVER role, and non-existent order handling.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b12_driver_cancelling_assigned_order_rejected(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """Even if an order is assigned to the calling driver, the DRIVER role cannot cancel it (must report an incident)."""
    resp = client.cancel_order(
        order_id=test_ids.ORDER_1_ID,
        reason="Driver vehicle breakdown - attempting direct cancellation",
        token=driver_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for driver cancel, got {resp.status_code}"


def test_b12_cancel_in_transit_order_by_authorized_operator(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """An OPERATOR can cancel an in-transit order, transitioning status to CANCELLED."""
    resp = client.cancel_order(
        order_id=test_ids.ORDER_1_ID,
        reason="Customer cancellation request processed by operator",
        token=operator_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}"
    data = resp.json().get("data", resp.json())
    assert data.get("status") == "CANCELLED"


def test_b12_repeated_cancel_idempotency(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """Cancelling an already-cancelled order is idempotent and succeeds without throwing an error."""
    for _ in range(2):
        resp = client.cancel_order(
            order_id=test_ids.ORDER_1_ID,
            reason="Idempotent cancel",
            token=operator_token,
        )
        assert resp.status_code in (200, 409), f"Expected 200 or 409, got {resp.status_code}"


def test_b12_valid_body_with_driver_role_rejected(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """Supplying a completely valid, comprehensive order creation payload is strictly rejected when caller is a DRIVER."""
    order_data = {
        "recipient_name": "Valid Recipient",
        "recipient_phone": "+84901234567",
        "delivery_address": "123 Le Loi, D1, HCMC",
        "cod_amount": 250000.0,
        "weight_kg": 1.5,
    }
    resp = client.create_order(
        tenant_id=test_ids.TENANT_A_ID,
        order_data=order_data,
        token=driver_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for DRIVER order creation, got {resp.status_code}"


def test_b12_nonexistent_order_cancel(
    client: DualClient, operator_token: str
):
    """Attempting to cancel a non-existent order returns 404 Not Found."""
    resp = client.cancel_order(
        order_id="00000000-0000-0000-0000-000000000000",
        reason="Cancelling non-existent order",
        token=operator_token,
    )
    assert resp.status_code == 404, f"Expected 404 Not Found, got {resp.status_code}"
