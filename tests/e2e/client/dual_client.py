"""
Dual-mode client seamlessly routing requests to LiveClient or MockContractClient.
Supports automatic environment probe and mock fallback.
"""

import os
from typing import Any, Dict, Optional
import requests

from tests.e2e.client.base_client import BaseClient, ResponseWrapper
from tests.e2e.client.live_client import LiveClient
from tests.e2e.client.mock_contract_client import MockContractClient
from tests.e2e.config import GATEWAY_URL, TEST_MODE


class DualClient(BaseClient):
    """
    Intelligently routes calls to live microservices if available or fallback to
    the high-fidelity in-memory contract simulator.
    """

    def __init__(self, mode: Optional[str] = None):
        self.mode = (mode or os.getenv("SWIFTROUTE_TEST_MODE", TEST_MODE)).lower()
        self.mock_client = MockContractClient()
        self.live_client = LiveClient()

        if self.mode == "live":
            self.active_client: BaseClient = self.live_client
            self.is_mock = False
        elif self.mode == "mock":
            self.active_client = self.mock_client
            self.is_mock = True
        else:  # "auto"
            if self._probe_live_gateway():
                self.active_client = self.live_client
                self.is_mock = False
            else:
                self.active_client = self.mock_client
                self.is_mock = True

    def _probe_live_gateway(self) -> bool:
        """
        Probe the gateway to determine if live services are reachable AND running
        the updated security hardening code with test fixtures.
        If unreachable or running pre-fix containers, falls back to MockContractClient.
        """
        try:
            # Check if the public endpoint /api/auth/email/resend-public is permitted without auth (F05)
            # In pre-fix containers, this returns 401 Unauthorized.
            resp = requests.post(
                f"{GATEWAY_URL}/api/auth/email/resend-public",
                json={"email": "healthcheck@swiftroute.internal"},
                timeout=1.0,
            )
            return resp.status_code == 200
        except Exception:
            return False

    def request(
        self,
        method: str,
        path: str,
        headers: Optional[Dict[str, str]] = None,
        json: Optional[Any] = None,
        params: Optional[Dict[str, Any]] = None,
        token: Optional[str] = None,
    ) -> ResponseWrapper:
        return self.active_client.request(
            method=method, path=path, headers=headers, json=json, params=params, token=token
        )

    # ---------------- Auth methods ----------------

    def login(self, username: str, password: str) -> ResponseWrapper:
        return self.active_client.login(username, password)

    def refresh_token(
        self, refresh_token: str, tenant_id: Optional[str] = None, token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.refresh_token(refresh_token, tenant_id=tenant_id, token=token)

    def invite_member(
        self, tenant_id: str, email: str, role: str, token: str
    ) -> ResponseWrapper:
        return self.active_client.invite_member(tenant_id, email, role, token)

    def suspend_member(
        self, tenant_id: str, member_id: str, reason: Optional[str] = None, token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.suspend_member(tenant_id, member_id, reason=reason, token=token)

    def resend_public_email(self, email: str) -> ResponseWrapper:
        return self.active_client.resend_public_email(email)

    # ---------------- Tenant & Workflow ----------------

    def get_tenant_workflow(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        return self.active_client.get_tenant_workflow(tenant_id, token=token)

    def get_tenant_kyc(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        return self.active_client.get_tenant_kyc(tenant_id, token=token)

    def poll_provisioning(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        return self.active_client.poll_provisioning(tenant_id, token=token)

    # ---------------- Orders & Incidents ----------------

    def create_order(
        self, tenant_id: str, order_data: Dict[str, Any], token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.create_order(tenant_id, order_data, token=token)

    def cancel_order(
        self, order_id: str, reason: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.cancel_order(order_id, reason, token=token, tenant_id=tenant_id)

    def get_order(
        self, order_id: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.get_order(order_id, token=token, tenant_id=tenant_id)

    def report_incident(
        self, order_id: str, incident_data: Dict[str, Any], token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.report_incident(order_id, incident_data, token=token, tenant_id=tenant_id)

    # ---------------- Dispatch & Simulation ----------------

    def acknowledge_plan(
        self, plan_id: str, notes: Optional[str] = None, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.acknowledge_plan(plan_id, notes=notes, token=token, tenant_id=tenant_id)

    def decline_plan(
        self, plan_id: str, reason: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.decline_plan(plan_id, reason=reason, token=token, tenant_id=tenant_id)

    def list_driver_plans(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.list_driver_plans(token=token, tenant_id=tenant_id)

    def get_plan_tracking_stream(
        self, plan_id: str, token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.get_plan_tracking_stream(plan_id, token=token)

    def start_tracking_simulation(
        self, plan_id: str, token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.start_tracking_simulation(plan_id, token=token)

    def stop_tracking_simulation(
        self, plan_id: str, token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.stop_tracking_simulation(plan_id, token=token)

    def get_driver_simulation_stream(
        self, token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.get_driver_simulation_stream(token=token)

    # ---------------- Driver & Settlements ----------------

    def approve_settlement(
        self, settlement_id: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.approve_settlement(settlement_id, token=token, tenant_id=tenant_id)

    def mark_settlement_paid(
        self, settlement_id: str, payout_data: Dict[str, Any], token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.mark_settlement_paid(settlement_id, payout_data, token=token, tenant_id=tenant_id)

    def list_settlements(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.list_settlements(token=token, tenant_id=tenant_id)

    def get_earnings(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.get_earnings(token=token, tenant_id=tenant_id)

    def get_all_drivers_internal(
        self, service_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.get_all_drivers_internal(service_key=service_key, tenant_id=tenant_id)

    # ---------------- Routing Engine ----------------

    def optimize_batch(
        self, batch_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.optimize_batch(
            batch_data, token=token, internal_key=internal_key, tenant_id=tenant_id
        )

    def optimize_insert(
        self, insert_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.optimize_insert(
            insert_data, token=token, internal_key=internal_key, tenant_id=tenant_id
        )

    def optimize_reoptimize(
        self, reoptimize_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.optimize_reoptimize(
            reoptimize_data, token=token, internal_key=internal_key, tenant_id=tenant_id
        )

    def get_routing_plans(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.get_routing_plans(token=token, tenant_id=tenant_id)

    # ---------------- Notifications ----------------

    def get_dispatch_alerts(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.get_dispatch_alerts(token=token, tenant_id=tenant_id)

    def get_notification_inbox(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.get_notification_inbox(token=token, tenant_id=tenant_id)

    def get_notification_stream(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        return self.active_client.get_notification_stream(token=token, tenant_id=tenant_id)

    def emit_event(self, event_type: str, payload: Dict[str, Any]) -> bool:
        return self.active_client.emit_event(event_type, payload)

    def reset_state(self) -> None:
        self.mock_client.reset_state()
        self.live_client.reset_state()
