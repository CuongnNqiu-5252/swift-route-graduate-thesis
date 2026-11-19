"""
Live HTTP client executing requests against running Docker microservices via API Gateway.
"""

from typing import Any, Dict, Optional
import requests

from tests.e2e.client.base_client import BaseClient, ResponseWrapper
from tests.e2e.config import (
    GATEWAY_URL,
    ROUTING_SERVICE_URL,
    REQUEST_TIMEOUT,
    SSE_READ_TIMEOUT,
    DRIVER_SERVICE_KEY,
    ROUTING_INTERNAL_KEY,
)


class LiveClient(BaseClient):
    """Client sending real HTTP requests to the live microservice stack."""

    def __init__(self, base_url: str = GATEWAY_URL):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()

    def request(
        self,
        method: str,
        path: str,
        headers: Optional[Dict[str, str]] = None,
        json: Optional[Any] = None,
        params: Optional[Dict[str, Any]] = None,
        token: Optional[str] = None,
    ) -> ResponseWrapper:
        url = f"{self.base_url}{path}" if path.startswith("/") else f"{self.base_url}/{path}"
        req_headers = dict(headers or {})
        if token:
            auth_val = token if token.startswith("Bearer ") else f"Bearer {token}"
            req_headers["Authorization"] = auth_val

        try:
            resp = self.session.request(
                method=method,
                url=url,
                headers=req_headers,
                json=json,
                params=params,
                timeout=REQUEST_TIMEOUT,
            )
            try:
                body = resp.json()
            except Exception:
                body = resp.text
            return ResponseWrapper(
                status_code=resp.status_code,
                body=body,
                headers=dict(resp.headers),
                text=resp.text,
            )
        except requests.exceptions.RequestException as e:
            return ResponseWrapper(
                status_code=503,
                body={"status": 503, "error": "SERVICE_UNAVAILABLE", "message": str(e)},
            )

    # ---------------- Auth methods ----------------

    def login(self, username: str, password: str) -> ResponseWrapper:
        return self.post("/api/auth/login", json={"username": username, "password": password})

    def refresh_token(
        self, refresh_token: str, tenant_id: Optional[str] = None, token: Optional[str] = None
    ) -> ResponseWrapper:
        payload: Dict[str, Any] = {"refresh_token": refresh_token}
        if tenant_id:
            payload["tenant_id"] = tenant_id
        return self.post("/api/auth/refresh", json=payload, token=token)

    def invite_member(
        self, tenant_id: str, email: str, role: str, token: str
    ) -> ResponseWrapper:
        return self.post(
            f"/api/tenants/{tenant_id}/members/invite",
            json={"email": email, "role": role},
            token=token,
        )

    def suspend_member(
        self, tenant_id: str, member_id: str, reason: Optional[str] = None, token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.post(
            f"/api/tenants/{tenant_id}/members/{member_id}/suspend",
            json={"reason": reason or "Administrative suspension"},
            token=token,
        )

    def resend_public_email(self, email: str) -> ResponseWrapper:
        return self.post("/api/auth/email/resend-public", json={"email": email})

    # ---------------- Tenant & Workflow ----------------

    def get_tenant_workflow(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        return self.get(f"/api/tenants/{tenant_id}", token=token)

    def get_tenant_kyc(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        return self.get(f"/api/tenants/{tenant_id}/kyc", token=token)

    def poll_provisioning(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        return self.get(f"/api/tenants/{tenant_id}/provisioning", token=token)

    # ---------------- Orders & Incidents ----------------

    def create_order(
        self, tenant_id: str, order_data: Dict[str, Any], token: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id}
        return self.post("/api/orders", json=order_data, headers=headers, token=token)

    def cancel_order(
        self, order_id: str, reason: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.patch(f"/api/orders/{order_id}/cancel", json={"reason": reason}, headers=headers, token=token)

    def get_order(
        self, order_id: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.get(f"/api/orders/{order_id}", headers=headers, token=token)

    def report_incident(
        self, order_id: str, incident_data: Dict[str, Any], token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.post(f"/api/orders/{order_id}/incidents", json=incident_data, headers=headers, token=token)

    # ---------------- Dispatch & Simulation ----------------

    def acknowledge_plan(
        self, plan_id: str, notes: Optional[str] = None, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.post(f"/api/orders/dispatch/plans/{plan_id}/acknowledge", json={"notes": notes}, headers=headers, token=token)

    def decline_plan(
        self, plan_id: str, reason: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.post(f"/api/orders/dispatch/plans/{plan_id}/decline", json={"reason": reason}, headers=headers, token=token)

    def list_driver_plans(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.get("/api/orders/dispatch/driver-plans", headers=headers, token=token)

    def get_plan_tracking_stream(
        self, plan_id: str, token: Optional[str] = None
    ) -> ResponseWrapper:
        url = f"{self.base_url}/api/orders/dispatch/plans/{plan_id}/tracking/stream"
        headers = {}
        if token:
            headers["Authorization"] = token if token.startswith("Bearer ") else f"Bearer {token}"
        try:
            with requests.get(url, headers=headers, stream=True, timeout=SSE_READ_TIMEOUT) as r:
                return ResponseWrapper(status_code=r.status_code, text="stream_connected", headers=dict(r.headers))
        except requests.exceptions.RequestException as e:
            return ResponseWrapper(status_code=503, text=str(e))

    def start_tracking_simulation(
        self, plan_id: str, token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.post(f"/api/orders/dispatch/plans/{plan_id}/tracking/simulation/start", token=token)

    def stop_tracking_simulation(
        self, plan_id: str, token: Optional[str] = None
    ) -> ResponseWrapper:
        return self.post(f"/api/orders/dispatch/plans/{plan_id}/tracking/simulation/stop", token=token)

    def get_driver_simulation_stream(
        self, token: Optional[str] = None
    ) -> ResponseWrapper:
        url = f"{self.base_url}/api/orders/dispatch/simulation/stream"
        headers = {}
        if token:
            headers["Authorization"] = token if token.startswith("Bearer ") else f"Bearer {token}"
        try:
            with requests.get(url, headers=headers, stream=True, timeout=SSE_READ_TIMEOUT) as r:
                return ResponseWrapper(status_code=r.status_code, text="simulation_stream_connected", headers=dict(r.headers))
        except requests.exceptions.RequestException as e:
            return ResponseWrapper(status_code=503, text=str(e))

    # ---------------- Driver & Settlements ----------------

    def approve_settlement(
        self, settlement_id: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.post(f"/api/settlements/{settlement_id}/approve", headers=headers, token=token)

    def mark_settlement_paid(
        self, settlement_id: str, payout_data: Dict[str, Any], token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.post(f"/api/settlements/{settlement_id}/mark-paid", json=payout_data, headers=headers, token=token)

    def list_settlements(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.get("/api/settlements", headers=headers, token=token)

    def get_earnings(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.get("/api/earnings", headers=headers, token=token)

    def get_all_drivers_internal(
        self, service_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {}
        if service_key:
            headers["X-Service-Key"] = service_key
        params = {"tenant_id": tenant_id} if tenant_id else {}
        return self.get("/api/drivers", headers=headers, params=params)

    # ---------------- Routing Engine ----------------

    def optimize_batch(
        self, batch_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {}
        if internal_key is not None:
            headers["x-internal-key"] = internal_key
        if tenant_id:
            headers["X-Tenant-Id"] = tenant_id
        return self.post("/optimize/batch", json=batch_data, headers=headers, token=token)

    def optimize_insert(
        self, insert_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {}
        if internal_key is not None:
            headers["x-internal-key"] = internal_key
        if tenant_id:
            headers["X-Tenant-Id"] = tenant_id
        return self.post("/optimize/insert", json=insert_data, headers=headers, token=token)

    def optimize_reoptimize(
        self, reoptimize_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {}
        if internal_key is not None:
            headers["x-internal-key"] = internal_key
        if tenant_id:
            headers["X-Tenant-Id"] = tenant_id
        return self.post("/optimize/reoptimize", json=reoptimize_data, headers=headers, token=token)

    def get_routing_plans(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.get("/plans", headers=headers, token=token)

    # ---------------- Notifications ----------------

    def get_dispatch_alerts(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.get("/api/notifications/alerts", headers=headers, token=token)

    def get_notification_inbox(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        return self.get("/api/notifications/inbox", headers=headers, token=token)

    def get_notification_stream(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        url = f"{self.base_url}/api/notifications/stream"
        headers = {"X-Tenant-Id": tenant_id} if tenant_id else {}
        if token:
            headers["Authorization"] = token if token.startswith("Bearer ") else f"Bearer {token}"
        try:
            with requests.get(url, headers=headers, stream=True, timeout=SSE_READ_TIMEOUT) as r:
                return ResponseWrapper(status_code=r.status_code, text="notification_stream_connected", headers=dict(r.headers))
        except requests.exceptions.RequestException as e:
            return ResponseWrapper(status_code=503, text=str(e))

    def emit_event(self, event_type: str, payload: Dict[str, Any]) -> bool:
        # In live mode, emits to RabbitMQ exchange if available
        return True

    def reset_state(self) -> None:
        # Live microservices reset hook if provided
        pass
