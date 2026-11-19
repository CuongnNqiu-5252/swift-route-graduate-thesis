"""
B14 (R2-ReportIncidentAuthorization Boundary): Edge & Corner Case Testing for Incident Authorization.
Verifies non-existent order incident rejection, unassigned driver incident attempt defense,
reassigned driver rejection, authorized dispatcher success with detailed payload, and VIEWER role rejection.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_b14_incident_on_nonexistent_order_returns_404(
    client: DualClient, driver_token: str
):
    """Submitting an incident report on a non-existent order UUID returns 404 Not Found."""
    incident_data = {
        "incident_type": "VEHICLE_BREAKDOWN",
        "description": "Flat tire on delivery route",
    }
    resp = client.report_incident(
        order_id="00000000-0000-0000-0000-000000000000",
        incident_data=incident_data,
        token=driver_token,
    )
    assert resp.status_code == 404, f"Expected 404 Not Found, got {resp.status_code}"


def test_b14_unassigned_driver_incident_attempt_rejected(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """DRIVER 1 cannot submit an incident report for ORDER_2 which is assigned to DRIVER 2 (fails with 403)."""
    incident_data = {
        "incident_type": "PACKAGE_DAMAGED",
        "description": "Unauthorized incident filing",
    }
    resp = client.report_incident(
        order_id=test_ids.ORDER_2_ID,
        incident_data=incident_data,
        token=driver_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for unassigned driver, got {resp.status_code}"


def test_b14_reassigned_driver_incident_attempt_rejected(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """If an order is reassigned away from a driver, that driver can no longer report incidents on it."""
    if client.is_mock:
        client.mock_client.orders[test_ids.ORDER_1_ID]["assigned_driver_id"] = test_ids.DRIVER_2_USER_ID

    incident_data = {
        "incident_type": "WEATHER_DELAY",
        "description": "Attempting incident after reassignment",
    }
    resp = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data=incident_data,
        token=driver_token,
    )
    assert resp.status_code == 403, f"Expected 403 after reassignment, got {resp.status_code}"


def test_b14_authorized_dispatcher_incident_report(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """An authorized OPERATOR can report an incident with a lengthy description and specific enum type."""
    incident_data = {
        "incident_type": "ACCIDENT",
        "description": "Detailed incident report: " + ("Heavy traffic and road detour. " * 10),
    }
    resp = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data=incident_data,
        token=operator_token,
    )
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}"
    data = resp.json().get("data", resp.json())
    assert data.get("incident_type") == "ACCIDENT"


def test_b14_viewer_role_incident_report_rejected(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """The read-only VIEWER role cannot submit incident reports (must fail with 403 Forbidden)."""
    incident_data = {
        "incident_type": "CUSTOMER_UNAVAILABLE",
        "description": "Viewer attempting mutation",
    }
    resp = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data=incident_data,
        token=viewer_token,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for VIEWER, got {resp.status_code}"
