"""
Base client interface and unified HTTP response wrapper.
"""

from abc import ABC, abstractmethod
import json
from typing import Any, Dict, List, Optional


class ResponseWrapper:
    """Standardized response representation for both live HTTP and in-memory mock."""

    def __init__(
        self,
        status_code: int,
        body: Any = None,
        headers: Optional[Dict[str, str]] = None,
        text: Optional[str] = None,
    ):
        self.status_code = status_code
        self.headers = headers or {}
        if isinstance(body, (dict, list)):
            self._json = body
            self.text = text or json.dumps(body, default=lambda o: list(o) if isinstance(o, set) else str(o))
        elif isinstance(body, str):
            self.text = body
            try:
                self._json = json.loads(body)
            except Exception:
                self._json = None
        else:
            self.text = text or ""
            self._json = None

    def json(self) -> Any:
        if self._json is not None:
            return self._json
        if self.text:
            try:
                return json.loads(self.text)
            except Exception:
                pass
        return {}

    @property
    def is_success(self) -> bool:
        return 200 <= self.status_code < 300

    @property
    def is_client_error(self) -> bool:
        return 400 <= self.status_code < 500

    @property
    def is_server_error(self) -> bool:
        return self.status_code >= 500

    def __repr__(self) -> str:
        return f"<ResponseWrapper [{self.status_code}]>"


class BaseClient(ABC):
    """Abstract interface defining all required operations across services."""

    @abstractmethod
    def request(
        self,
        method: str,
        path: str,
        headers: Optional[Dict[str, str]] = None,
        json: Optional[Any] = None,
        params: Optional[Dict[str, Any]] = None,
        token: Optional[str] = None,
    ) -> ResponseWrapper:
        """Low-level HTTP request dispatcher."""
        pass

    def get(
        self,
        path: str,
        headers: Optional[Dict[str, str]] = None,
        params: Optional[Dict[str, Any]] = None,
        token: Optional[str] = None,
    ) -> ResponseWrapper:
        return self.request("GET", path, headers=headers, params=params, token=token)

    def post(
        self,
        path: str,
        headers: Optional[Dict[str, str]] = None,
        json: Optional[Any] = None,
        params: Optional[Dict[str, Any]] = None,
        token: Optional[str] = None,
    ) -> ResponseWrapper:
        return self.request("POST", path, headers=headers, json=json, params=params, token=token)

    def put(
        self,
        path: str,
        headers: Optional[Dict[str, str]] = None,
        json: Optional[Any] = None,
        params: Optional[Dict[str, Any]] = None,
        token: Optional[str] = None,
    ) -> ResponseWrapper:
        return self.request("PUT", path, headers=headers, json=json, params=params, token=token)

    def patch(
        self,
        path: str,
        headers: Optional[Dict[str, str]] = None,
        json: Optional[Any] = None,
        params: Optional[Dict[str, Any]] = None,
        token: Optional[str] = None,
    ) -> ResponseWrapper:
        return self.request("PATCH", path, headers=headers, json=json, params=params, token=token)

    def delete(
        self,
        path: str,
        headers: Optional[Dict[str, str]] = None,
        params: Optional[Dict[str, Any]] = None,
        token: Optional[str] = None,
    ) -> ResponseWrapper:
        return self.request("DELETE", path, headers=headers, params=params, token=token)

    # ---------------- High-level domain methods ----------------

    # Auth
    @abstractmethod
    def login(self, username: str, password: str) -> ResponseWrapper:
        pass

    @abstractmethod
    def refresh_token(
        self, refresh_token: str, tenant_id: Optional[str] = None, token: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def invite_member(
        self, tenant_id: str, email: str, role: str, token: str
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def suspend_member(
        self, tenant_id: str, member_id: str, reason: Optional[str] = None, token: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def resend_public_email(self, email: str) -> ResponseWrapper:
        pass

    # Tenant & Workflow
    @abstractmethod
    def get_tenant_workflow(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        pass

    @abstractmethod
    def get_tenant_kyc(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        pass

    @abstractmethod
    def poll_provisioning(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        pass

    # Orders & Incidents
    @abstractmethod
    def create_order(
        self, tenant_id: str, order_data: Dict[str, Any], token: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def cancel_order(
        self, order_id: str, reason: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def get_order(
        self, order_id: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def report_incident(
        self, order_id: str, incident_data: Dict[str, Any], token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    # Dispatch & Simulation
    @abstractmethod
    def acknowledge_plan(
        self, plan_id: str, notes: Optional[str] = None, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def decline_plan(
        self, plan_id: str, reason: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def list_driver_plans(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def get_plan_tracking_stream(
        self, plan_id: str, token: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def start_tracking_simulation(
        self, plan_id: str, token: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def stop_tracking_simulation(
        self, plan_id: str, token: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def get_driver_simulation_stream(
        self, token: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    # Driver & Settlements
    @abstractmethod
    def approve_settlement(
        self, settlement_id: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def mark_settlement_paid(
        self, settlement_id: str, payout_data: Dict[str, Any], token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def list_settlements(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def get_earnings(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def get_all_drivers_internal(
        self, service_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    # Routing Engine
    @abstractmethod
    def optimize_batch(
        self, batch_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def optimize_insert(
        self, insert_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def optimize_reoptimize(
        self, reoptimize_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def get_routing_plans(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    # Notifications & Events
    @abstractmethod
    def get_dispatch_alerts(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def get_notification_inbox(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def get_notification_stream(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        pass

    @abstractmethod
    def emit_event(
        self, event_type: str, payload: Dict[str, Any]
    ) -> bool:
        pass

    @abstractmethod
    def reset_state(self) -> None:
        """Reset internal mock or state fixtures between runs."""
        pass
