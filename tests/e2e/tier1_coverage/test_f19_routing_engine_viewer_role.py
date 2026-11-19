"""
F19 (R4-RoutingEngineViewerRole): Role-Based Access Control on Route Optimization APIs.
Verifies that VIEWER role is blocked from directly invoking optimization APIs
(batch, insert, reoptimize) while read-only access is preserved and DRIVER role is not restricted.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f19_viewer_cannot_call_batch_optimize(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """VIEWER role calling POST /optimize/batch is rejected with 403 Forbidden."""
    batch_data = {
        "depot": {"lat": 10.7769, "lng": 106.7009},
        "orders": [{"order_id": test_ids.ORDER_1_ID, "lat": 10.78, "lng": 106.71}],
        "vehicles": [{"vehicle_id": "v-1", "capacity": 100}],
    }
    resp = client.optimize_batch(batch_data, token=viewer_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 403, f"Expected 403 Forbidden for VIEWER batch optimize, got {resp.status_code}: {resp.text}"


def test_f19_viewer_cannot_call_insert_optimize(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """VIEWER role calling POST /optimize/insert is rejected with 403 Forbidden."""
    insert_data = {
        "existing_route": [test_ids.ORDER_1_ID],
        "new_order": {"order_id": test_ids.ORDER_2_ID, "lat": 10.79, "lng": 106.72},
    }
    resp = client.optimize_insert(insert_data, token=viewer_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 403, f"Expected 403 Forbidden for VIEWER insert optimize, got {resp.status_code}: {resp.text}"


def test_f19_viewer_can_call_get_plans(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """VIEWER role can access read-only route planning inspection endpoints (returns 200)."""
    resp = client.get_routing_plans(token=viewer_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 200, f"Expected 200 OK for VIEWER route plans read, got {resp.status_code}: {resp.text}"


def test_f19_driver_can_call_reoptimize(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER role is NOT restricted from invoking dynamic reoptimization along their route."""
    reopt_data = {
        "current_lat": 10.7769,
        "current_lng": 106.7009,
        "remaining_orders": [test_ids.ORDER_1_ID],
    }
    resp = client.optimize_reoptimize(reopt_data, token=driver_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 200, f"Expected 200 OK for DRIVER reoptimization, got {resp.status_code}: {resp.text}"


def test_f19_operator_can_call_batch_optimize(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """OPERATOR role can invoke batch optimization successfully (returns 200)."""
    batch_data = {
        "depot": {"lat": 10.7769, "lng": 106.7009},
        "orders": [{"order_id": test_ids.ORDER_1_ID, "lat": 10.78, "lng": 106.71}],
        "vehicles": [{"vehicle_id": "v-1", "capacity": 100}],
    }
    resp = client.optimize_batch(batch_data, token=operator_token, tenant_id=test_ids.TENANT_A_ID)
    assert resp.status_code == 200, f"Expected 200 OK for OPERATOR batch optimize, got {resp.status_code}: {resp.text}"
