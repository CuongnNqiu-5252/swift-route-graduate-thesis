"""
F14 (R2-ReportIncidentAuthorization): Authorization on Incident Reporting.
Verifies that OrderService.reportIncident restricts reporting to authorized dispatchers
(OWNER, TENANT_ADMIN, OPERATOR, SYSTEM_ADMIN) or the assigned driver.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f14_assigned_driver_can_report_incident(
    client: DualClient, driver_token: str, test_ids: DeterministicIDs
):
    """The assigned DRIVER can report an operational incident on their order."""
    incident_data = {
        "incident_type": "TRAFFIC_ACCIDENT",
        "description": "Minor vehicle collision, package safe but delayed",
    }
    resp = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data=incident_data,
        token=driver_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code in (200, 201), f"Expected 200/201, got {resp.status_code}: {resp.text}"


def test_f14_unassigned_driver_cannot_report_incident(
    client: DualClient, driver_2_token: str, test_ids: DeterministicIDs
):
    """A DRIVER not assigned to the order cannot submit incident reports (rejected with 403)."""
    incident_data = {
        "incident_type": "WEATHER_DELAY",
        "description": "Unauthorized driver reporting incident",
    }
    resp = client.report_incident(
        order_id=test_ids.ORDER_1_ID,  # Assigned to DRIVER_USER_ID, not DRIVER_2
        incident_data=incident_data,
        token=driver_2_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden, got {resp.status_code}: {resp.text}"


def test_f14_authorized_dispatcher_can_report_incident(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """An OPERATOR (authorized dispatcher) can report incidents on any tenant order."""
    incident_data = {
        "incident_type": "CUSTOMER_UNAVAILABLE",
        "description": "Customer did not answer door after 3 attempts",
    }
    resp = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data=incident_data,
        token=operator_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code in (200, 201), f"Expected 200/201, got {resp.status_code}: {resp.text}"


def test_f14_customer_or_unauthorized_cannot_report_incident(
    client: DualClient, viewer_token: str, test_ids: DeterministicIDs
):
    """Unauthorized callers (such as VIEWER) cannot report operational incidents."""
    incident_data = {
        "incident_type": "SECURITY_ALERT",
        "description": "Viewer attempting incident write",
    }
    resp = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data=incident_data,
        token=viewer_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code == 403, f"Expected 403 Forbidden for VIEWER, got {resp.status_code}: {resp.text}"


def test_f14_incident_reporting_tenant_boundary(
    client: DualClient, operator_token: str, test_ids: DeterministicIDs
):
    """Attempting to report an incident on an order in another tenant is rejected with 403/404."""
    resp = client.report_incident(
        order_id=test_ids.ORDER_TENANT_B_ID,
        incident_data={"incident_type": "DAMAGED_PACKAGE", "description": "Cross tenant incident"},
        token=operator_token,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp.status_code in (403, 404), f"Expected 403/404, got {resp.status_code}: {resp.text}"
