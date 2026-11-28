"""
Deterministic in-memory mock contract simulator for SwiftRoute E2E testing.
Implements all 21 security rules, role boundaries, and response envelopes.
"""

from datetime import datetime, timezone
import json
import re
from typing import Any, Dict, List, Optional, Set
from jose import jwt, JWTError

from tests.e2e.client.base_client import BaseClient, ResponseWrapper
from tests.e2e.config import (
    IDS,
    JWT_SECRET,
    JWT_ALGORITHM,
    DRIVER_SERVICE_KEY,
    ROUTING_INTERNAL_KEY,
)


class MockContractClient(BaseClient):
    """
    Self-contained in-memory contract engine verifying all 21 security specifications
    consistently across environments without external network dependencies.
    """

    def __init__(self):
        self.server_driver_service_key: Optional[str] = DRIVER_SERVICE_KEY
        self.server_routing_internal_key: Optional[str] = ROUTING_INTERNAL_KEY
        self.reset_state()

    def reset_state(self) -> None:
        """Reset internal database fixtures to pristine state."""
        self.server_driver_service_key = DRIVER_SERVICE_KEY
        self.server_routing_internal_key = ROUTING_INTERNAL_KEY

        # Users table: userId -> metadata
        self.users: Dict[str, Dict[str, Any]] = {
            IDS.OWNER_USER_ID: {
                "id": IDS.OWNER_USER_ID,
                "email": "owner@tenanta.com",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {IDS.TENANT_A_ID: "OWNER"},
                "active_refresh_tokens": {"owner_rt_valid_1"},
            },
            IDS.ADMIN_USER_ID: {
                "id": IDS.ADMIN_USER_ID,
                "email": "admin@tenanta.com",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {IDS.TENANT_A_ID: "TENANT_ADMIN"},
                "active_refresh_tokens": {"admin_rt_valid_1"},
            },
            IDS.OPERATOR_USER_ID: {
                "id": IDS.OPERATOR_USER_ID,
                "email": "operator@tenanta.com",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {IDS.TENANT_A_ID: "OPERATOR"},
                "active_refresh_tokens": {"operator_rt_valid_1"},
            },
            IDS.DRIVER_USER_ID: {
                "id": IDS.DRIVER_USER_ID,
                "email": "driver1@tenanta.com",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {IDS.TENANT_A_ID: "DRIVER"},
                "active_refresh_tokens": {"driver_rt_valid_1"},
            },
            IDS.DRIVER_2_USER_ID: {
                "id": IDS.DRIVER_2_USER_ID,
                "email": "driver2@tenanta.com",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {IDS.TENANT_A_ID: "DRIVER"},
                "active_refresh_tokens": {"driver2_rt_valid_1"},
            },
            IDS.VIEWER_USER_ID: {
                "id": IDS.VIEWER_USER_ID,
                "email": "viewer@tenanta.com",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {IDS.TENANT_A_ID: "VIEWER"},
                "active_refresh_tokens": {"viewer_rt_valid_1"},
            },
            IDS.SYSTEM_ADMIN_USER_ID: {
                "id": IDS.SYSTEM_ADMIN_USER_ID,
                "email": "sysadmin@swiftroute.internal",
                "status": "ACTIVE",
                "system_role": "SYSTEM_ADMIN",
                "memberships": {},
                "active_refresh_tokens": {"sysadmin_rt_valid_1"},
            },
            IDS.UNSCOPED_USER_ID: {
                "id": IDS.UNSCOPED_USER_ID,
                "email": "unscoped@swiftroute.internal",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {},
                "active_refresh_tokens": {"unscoped_rt_valid_1"},
            },
            IDS.MULTI_TENANT_USER_ID: {
                "id": IDS.MULTI_TENANT_USER_ID,
                "email": "contractor@logistics.com",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {
                    IDS.TENANT_A_ID: "DRIVER",
                    IDS.TENANT_B_ID: "OPERATOR",
                },
                "active_refresh_tokens": {"multitenant_rt_valid_1"},
            },
            IDS.TENANT_B_ADMIN_USER_ID: {
                "id": IDS.TENANT_B_ADMIN_USER_ID,
                "email": "admin@tenantb.com",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {IDS.TENANT_B_ID: "TENANT_ADMIN"},
                "active_refresh_tokens": {"tenantb_admin_rt_1"},
            },
            IDS.TENANT_B_DRIVER_USER_ID: {
                "id": IDS.TENANT_B_DRIVER_USER_ID,
                "email": "driver@tenantb.com",
                "status": "ACTIVE",
                "system_role": "USER",
                "memberships": {IDS.TENANT_B_ID: "DRIVER"},
                "active_refresh_tokens": {"tenantb_driver_rt_1"},
            },
        }

        # Revoked refresh tokens set (for family invalidation)
        self.revoked_refresh_tokens: Set[str] = set()

        # Tenants table
        self.tenants: Dict[str, Dict[str, Any]] = {
            IDS.TENANT_A_ID: {
                "id": IDS.TENANT_A_ID,
                "name": "Tenant A Logistics",
                "status": "ACTIVE",
                "kyc_data": {"registration_number": "REG-A-100", "verified": True},
                "creator_id": IDS.OWNER_USER_ID,
            },
            IDS.TENANT_B_ID: {
                "id": IDS.TENANT_B_ID,
                "name": "Tenant B Express",
                "status": "ACTIVE",
                "kyc_data": {"registration_number": "REG-B-200", "verified": True},
                "creator_id": IDS.TENANT_B_ADMIN_USER_ID,
            },
            IDS.TENANT_SUSPENDED_ID: {
                "id": IDS.TENANT_SUSPENDED_ID,
                "name": "Suspended Tenant LLC",
                "status": "SUSPENDED",
                "kyc_data": {"registration_number": "REG-S-999", "verified": False},
                "creator_id": "suspended-creator",
            },
            IDS.TENANT_PENDING_ID: {
                "id": IDS.TENANT_PENDING_ID,
                "name": "Pending Verification Corp",
                "status": "PENDING",
                "kyc_data": {"registration_number": "REG-P-444", "verified": False},
                "creator_id": IDS.UNSCOPED_USER_ID,
            },
        }

        # Orders table
        self.orders: Dict[str, Dict[str, Any]] = {
            IDS.ORDER_1_ID: {
                "id": IDS.ORDER_1_ID,
                "tenant_id": IDS.TENANT_A_ID,
                "status": "IN_TRANSIT",
                "assigned_driver_id": IDS.DRIVER_USER_ID,
                "recipient_name": "Customer One",
                "cod_amount": 150000.0,
            },
            IDS.ORDER_2_ID: {
                "id": IDS.ORDER_2_ID,
                "tenant_id": IDS.TENANT_A_ID,
                "status": "PENDING",
                "assigned_driver_id": IDS.DRIVER_2_USER_ID,
                "recipient_name": "Customer Two",
                "cod_amount": 250000.0,
            },
            IDS.ORDER_TENANT_B_ID: {
                "id": IDS.ORDER_TENANT_B_ID,
                "tenant_id": IDS.TENANT_B_ID,
                "status": "IN_TRANSIT",
                "assigned_driver_id": IDS.TENANT_B_DRIVER_USER_ID,
                "recipient_name": "Customer B",
                "cod_amount": 300000.0,
            },
        }

        # Dispatch plans table
        self.dispatch_plans: Dict[str, Dict[str, Any]] = {
            IDS.PLAN_1_ID: {
                "id": IDS.PLAN_1_ID,
                "tenant_id": IDS.TENANT_A_ID,
                "driver_id": IDS.DRIVER_USER_ID,
                "status": "DISPATCHED",
                "stops": [{"order_id": IDS.ORDER_1_ID, "lat": 10.7769, "lng": 106.7009}],
            },
            IDS.PLAN_2_ID: {
                "id": IDS.PLAN_2_ID,
                "tenant_id": IDS.TENANT_A_ID,
                "driver_id": IDS.DRIVER_2_USER_ID,
                "status": "DISPATCHED",
                "stops": [{"order_id": IDS.ORDER_2_ID, "lat": 10.7800, "lng": 106.7100}],
            },
            IDS.PLAN_TENANT_B_ID: {
                "id": IDS.PLAN_TENANT_B_ID,
                "tenant_id": IDS.TENANT_B_ID,
                "driver_id": IDS.TENANT_B_DRIVER_USER_ID,
                "status": "DISPATCHED",
                "stops": [{"order_id": IDS.ORDER_TENANT_B_ID, "lat": 21.0285, "lng": 105.8542}],
            },
        }

        # Settlements table
        self.settlements: Dict[str, Dict[str, Any]] = {
            IDS.SETTLEMENT_1_ID: {
                "id": IDS.SETTLEMENT_1_ID,
                "tenant_id": IDS.TENANT_A_ID,
                "driver_id": IDS.DRIVER_USER_ID,
                "amount": 1250000.0,
                "status": "PENDING",
            },
            IDS.SETTLEMENT_TENANT_B_ID: {
                "id": IDS.SETTLEMENT_TENANT_B_ID,
                "tenant_id": IDS.TENANT_B_ID,
                "driver_id": IDS.TENANT_B_DRIVER_USER_ID,
                "amount": 850000.0,
                "status": "PENDING",
            },
        }

        # Incidents table
        self.incidents: List[Dict[str, Any]] = []

        # Notifications & Alerts
        self.alerts: List[Dict[str, Any]] = [
            {
                "id": "alert-001",
                "tenant_id": IDS.TENANT_A_ID,
                "severity": "HIGH",
                "message": "Route delayed due to heavy weather",
                "timestamp": "2026-11-14T08:00:00Z",
            }
        ]
        self.inbox_items: List[Dict[str, Any]] = [
            {
                "id": "inbox-001",
                "tenant_id": IDS.TENANT_A_ID,
                "user_id": IDS.VIEWER_USER_ID,
                "title": "Shift summary ready",
                "body": "Daily operations summary has been generated",
                "read": False,
            }
        ]

        # Audit / log recording for sensitive token checks
        self.logged_lines: List[str] = []
        self.sse_broadcast_messages: List[Dict[str, Any]] = []

    # ---------------- Token verification helper ----------------

    def _verify_token(self, token: Optional[str]) -> Optional[Dict[str, Any]]:
        """Verify JWT token and return claims or None."""
        if not token:
            return None
        if token.startswith("Bearer "):
            token = token[7:]
        try:
            claims = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
            return claims
        except JWTError:
            return None

    def _make_error(self, status: int, code: str, message: str, path: str = "") -> ResponseWrapper:
        """Create standard error response matching SwiftRoute conventions."""
        body = {
            "status": status,
            "error": code,
            "message": message,
            "path": path,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        return ResponseWrapper(status_code=status, body=body)

    def _make_data(self, data: Any, status: int = 200) -> ResponseWrapper:
        """Create standard success response wrapping data."""
        body = {
            "data": data,
            "error": None,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        return ResponseWrapper(status_code=status, body=body)

    # ---------------- Low-level request dispatcher ----------------

    def request(
        self,
        method: str,
        path: str,
        headers: Optional[Dict[str, str]] = None,
        json: Optional[Any] = None,
        params: Optional[Dict[str, Any]] = None,
        token: Optional[str] = None,
    ) -> ResponseWrapper:
        headers = headers or {}
        auth_header = token or headers.get("Authorization")
        client_tenant_header = headers.get("X-Tenant-Id")

        # 1. Gateway Public Path Check (F05)
        public_paths = [
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/email/verify",
            "/api/auth/email/resend-public",
            "/api/auth/password/forgot",
            "/api/auth/password/reset",
            "/api/auth/refresh",
            "/actuator/health",
        ]
        if any(path.startswith(p) for p in public_paths):
            if path == "/api/auth/email/resend-public":
                return self.resend_public_email(json.get("email", "") if json else "")
            if path == "/api/auth/login":
                return self.login(json.get("username", ""), json.get("password", "")) if json else self._make_error(400, "BAD_REQUEST", "Missing credentials")
            if path == "/api/auth/refresh":
                rt = json.get("refresh_token") if json else None
                tid = json.get("tenant_id") if json else None
                return self.refresh_token(rt, tenant_id=tid)

        # 2. Authenticate token
        claims = self._verify_token(auth_header)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token", path)

        user_id = claims.get("sub")
        system_role = claims.get("system_role", "USER")
        token_tenant_id = claims.get("tenant_id")
        token_tenant_role = claims.get("tenant_role")
        is_system_admin = (system_role == "SYSTEM_ADMIN")

        # 3. Gateway Tenant Isolation Check (F04)
        if not token_tenant_id:
            # User has no tenant claim
            if client_tenant_header and not is_system_admin:
                return self._make_error(
                    403, "Forbidden", "Forbidden: Cross-tenant access denied", path
                )
        else:
            # User has tenant claim
            if client_tenant_header and client_tenant_header != token_tenant_id:
                if not is_system_admin:
                    return self._make_error(
                        403, "Forbidden", "Forbidden: Cross-tenant access denied", path
                    )

        # 4. Route dispatcher to mock handlers
        effective_tenant = client_tenant_header or token_tenant_id

        # Tenant & Workflow routes
        if path.startswith("/api/tenants/"):
            m_invite = re.match(r"^/api/tenants/([^/]+)/members/invite$", path)
            if m_invite and method == "POST":
                tenant_id = m_invite.group(1)
                return self.invite_member(tenant_id, json.get("email"), json.get("role"), auth_header)

            m_suspend = re.match(r"^/api/tenants/([^/]+)/members/([^/]+)/suspend$", path)
            if m_suspend and method == "POST":
                tenant_id = m_suspend.group(1)
                member_id = m_suspend.group(2)
                reason = json.get("reason") if json else None
                return self.suspend_member(tenant_id, member_id, reason, auth_header)

            m_kyc = re.match(r"^/api/tenants/([^/]+)/kyc$", path)
            if m_kyc and method == "GET":
                tenant_id = m_kyc.group(1)
                return self.get_tenant_kyc(tenant_id, auth_header)

            m_prov = re.match(r"^/api/tenants/([^/]+)/provisioning$", path)
            if m_prov and method == "GET":
                tenant_id = m_prov.group(1)
                return self.poll_provisioning(tenant_id, auth_header)

            m_tenant = re.match(r"^/api/tenants/([^/]+)$", path)
            if m_tenant and method == "GET":
                tenant_id = m_tenant.group(1)
                return self.get_tenant_workflow(tenant_id, auth_header)

        # Order routes
        if path.startswith("/api/orders"):
            if path == "/api/orders" and method == "POST":
                tenant_id = effective_tenant or IDS.TENANT_A_ID
                return self.create_order(tenant_id, json or {}, auth_header)

            m_cancel = re.match(r"^/api/orders/([^/]+)/cancel$", path)
            if m_cancel and method in ("PATCH", "POST"):
                order_id = m_cancel.group(1)
                reason = json.get("reason", "Cancelled") if json else "Cancelled"
                return self.cancel_order(order_id, reason, auth_header, tenant_id=effective_tenant)

            m_inc = re.match(r"^/api/orders/([^/]+)/incidents$", path)
            if m_inc and method == "POST":
                order_id = m_inc.group(1)
                return self.report_incident(order_id, json or {}, auth_header, tenant_id=effective_tenant)

            if path == "/api/orders/dispatch/driver-plans" and method == "GET":
                return self.list_driver_plans(auth_header, tenant_id=effective_tenant)

            if path == "/api/orders/dispatch/simulation/stream" and method == "GET":
                return self.get_driver_simulation_stream(auth_header)

            m_ack = re.match(r"^/api/orders/dispatch/plans/([^/]+)/acknowledge$", path)
            if m_ack and method == "POST":
                plan_id = m_ack.group(1)
                notes = json.get("notes") if json else None
                return self.acknowledge_plan(plan_id, notes, auth_header, tenant_id=effective_tenant)

            m_dec = re.match(r"^/api/orders/dispatch/plans/([^/]+)/decline$", path)
            if m_dec and method == "POST":
                plan_id = m_dec.group(1)
                reason = json.get("reason", "Declined") if json else "Declined"
                return self.decline_plan(plan_id, reason, auth_header, tenant_id=effective_tenant)

            m_trk = re.match(r"^/api/orders/dispatch/plans/([^/]+)/tracking/stream$", path)
            if m_trk and method == "GET":
                plan_id = m_trk.group(1)
                return self.get_plan_tracking_stream(plan_id, auth_header)

            m_sim_start = re.match(r"^/api/orders/dispatch/plans/([^/]+)/tracking/simulation/start$", path)
            if m_sim_start and method == "POST":
                plan_id = m_sim_start.group(1)
                return self.start_tracking_simulation(plan_id, auth_header)

            m_sim_stop = re.match(r"^/api/orders/dispatch/plans/([^/]+)/tracking/simulation/stop$", path)
            if m_sim_stop and method == "POST":
                plan_id = m_sim_stop.group(1)
                return self.stop_tracking_simulation(plan_id, auth_header)

            m_order = re.match(r"^/api/orders/([^/]+)$", path)
            if m_order and method == "GET":
                order_id = m_order.group(1)
                return self.get_order(order_id, auth_header, tenant_id=effective_tenant)

        # Settlements & Earnings
        if path.startswith("/api/settlements"):
            m_app = re.match(r"^/api/settlements/([^/]+)/approve$", path)
            if m_app and method == "POST":
                return self.approve_settlement(m_app.group(1), auth_header, tenant_id=effective_tenant)

            m_paid = re.match(r"^/api/settlements/([^/]+)/mark-paid$", path)
            if m_paid and method == "POST":
                return self.mark_settlement_paid(m_paid.group(1), json or {}, auth_header, tenant_id=effective_tenant)

            if path == "/api/settlements" and method == "GET":
                return self.list_settlements(auth_header, tenant_id=effective_tenant)

        if path == "/api/earnings" and method == "GET":
            return self.get_earnings(auth_header, tenant_id=effective_tenant)

        # Driver service internal / drivers list
        if path == "/api/drivers" and method == "GET":
            svc_key = headers.get("X-Service-Key")
            if svc_key:
                tid = params.get("tenant_id") if params else None
                return self.get_all_drivers_internal(svc_key, tenant_id=tid)
            if is_system_admin:
                drivers = [
                    {"id": u["id"], "email": u["email"], "system_role": u["system_role"]}
                    for u in self.users.values()
                ]
                return self._make_data(drivers)
            return self._make_error(403, "Forbidden", "Direct driver list restricted")

        # Routing engine
        if path == "/optimize/batch" and method == "POST":
            return self.optimize_batch(json or {}, auth_header, internal_key=headers.get("x-internal-key"), tenant_id=effective_tenant)
        if path == "/optimize/insert" and method == "POST":
            return self.optimize_insert(json or {}, auth_header, internal_key=headers.get("x-internal-key"), tenant_id=effective_tenant)
        if path == "/optimize/reoptimize" and method == "POST":
            return self.optimize_reoptimize(json or {}, auth_header, internal_key=headers.get("x-internal-key"), tenant_id=effective_tenant)
        if path == "/plans" and method == "GET":
            return self.get_routing_plans(auth_header, tenant_id=effective_tenant)

        # Notifications
        if path == "/api/notifications/alerts" and method == "GET":
            return self.get_dispatch_alerts(auth_header, tenant_id=effective_tenant)
        if path == "/api/notifications/inbox" and method == "GET":
            return self.get_notification_inbox(auth_header, tenant_id=effective_tenant)
        if path == "/api/notifications/stream" and method == "GET":
            return self.get_notification_stream(auth_header, tenant_id=effective_tenant)

        return self._make_error(404, "NOT_FOUND", f"Path {path} not found in mock router", path)

    # ---------------- Domain Methods ----------------

    # F01: Owner Protection & Member Management
    def invite_member(self, tenant_id: str, email: str, role: str, token: str) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        caller_system_role = claims.get("system_role", "USER")
        caller_tenant_id = claims.get("tenant_id")
        caller_role = claims.get("tenant_role")

        if caller_system_role != "SYSTEM_ADMIN" and caller_tenant_id != tenant_id:
            return self._make_error(403, "Forbidden", "Cross-tenant invitation not allowed")

        # F01 Rule 1: TENANT_ADMIN cannot invite OWNER
        if caller_role == "TENANT_ADMIN" and role == "OWNER":
            return self._make_error(403, "Forbidden", "TENANT_ADMIN cannot invite OWNER")

        # OWNER can invite OWNER or others
        invitation = {
            "invitation_id": f"inv-{int(datetime.now().timestamp())}",
            "tenant_id": tenant_id,
            "email": email,
            "role": role,
            "invited_by": caller_id,
            "status": "PENDING",
        }
        return self._make_data(invitation, status=201)

    def suspend_member(
        self, tenant_id: str, member_id: str, reason: Optional[str] = None, token: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        caller_system_role = claims.get("system_role", "USER")
        caller_tenant_id = claims.get("tenant_id")
        caller_role = claims.get("tenant_role")

        if caller_system_role != "SYSTEM_ADMIN" and caller_tenant_id != tenant_id:
            return self._make_error(403, "Forbidden", "Cross-tenant suspension not allowed")

        # F01 Rule 4: TENANT_ADMIN cannot self-suspend
        if caller_role == "TENANT_ADMIN" and member_id == caller_id:
            return self._make_error(403, "Forbidden", "TENANT_ADMIN cannot suspend self")

        # Lookup target user
        target_user = self.users.get(member_id)
        if not target_user:
            return self._make_error(404, "NOT_FOUND", "Member not found")

        target_role = target_user.get("memberships", {}).get(tenant_id)
        # F01 Rule 3: TENANT_ADMIN cannot suspend OWNER
        if caller_role == "TENANT_ADMIN" and target_role == "OWNER":
            return self._make_error(403, "Forbidden", "TENANT_ADMIN cannot suspend OWNER")

        # F02: Suspend member and revoke all active refresh tokens for user
        target_user["status"] = "SUSPENDED"
        target_user["memberships"][tenant_id] = "SUSPENDED"
        for rt in list(target_user.get("active_refresh_tokens", set())):
            self.revoked_refresh_tokens.add(rt)
        target_user["active_refresh_tokens"].clear()

        return self._make_data({
            "member_id": member_id,
            "tenant_id": tenant_id,
            "status": "SUSPENDED",
            "refresh_tokens_revoked": True,
        })

    # F02, F06, F07, F08: Auth & Token Refresh
    def login(self, username: str, password: str) -> ResponseWrapper:
        # Generic login mock
        for u in self.users.values():
            if u["email"] == username:
                if u["status"] != "ACTIVE":
                    return self._make_error(401, "INVALID_CREDENTIALS", "Invalid credentials")
                token_data = {
                    "access_token": "mock_jwt_token",
                    "refresh_token": f"rt_{u['id']}",
                    "token_type": "Bearer",
                    "expires_in": 900,
                }
                return self._make_data(token_data)
        return self._make_error(401, "INVALID_CREDENTIALS", "Invalid credentials")

    def refresh_token(
        self, refresh_token: str, tenant_id: Optional[str] = None, token: Optional[str] = None
    ) -> ResponseWrapper:
        # F02 Rule: If refresh token is in revoked list, reject with 401
        if refresh_token in self.revoked_refresh_tokens:
            return self._make_error(401, "INVALID_CREDENTIALS", "Refresh token has been revoked")

        # Find user owning this refresh token
        user = None
        for u in self.users.values():
            if refresh_token in u.get("active_refresh_tokens", set()):
                user = u
                break

        if not user:
            return self._make_error(401, "INVALID_CREDENTIALS", "Invalid refresh token")

        # F06 Rule: Require user.status == ACTIVE
        if user.get("status") != "ACTIVE":
            return self._make_error(401, "INVALID_CREDENTIALS", "User is not active")

        # F08 Rule: Retain active tenant session across token refreshes
        active_tenant = tenant_id
        if not active_tenant and token:
            bearer_claims = self._verify_token(token)
            if bearer_claims:
                active_tenant = bearer_claims.get("tenant_id")

        if not active_tenant:
            # Pick first active tenant membership
            active_memberships = [tid for tid, r in user.get("memberships", {}).items() if r != "SUSPENDED"]
            if active_memberships:
                active_tenant = active_memberships[0]

        # Check tenant status if selected
        if active_tenant:
            tenant_info = self.tenants.get(active_tenant)
            if tenant_info and tenant_info.get("status") == "SUSPENDED":
                return self._make_error(403, "Forbidden", "Tenant is suspended")

            caller_role = user.get("memberships", {}).get(active_tenant)
            if not caller_role or caller_role == "SUSPENDED":
                return self._make_error(403, "Forbidden", "User membership is not active in tenant")
        else:
            caller_role = None

        # F07: Configured 15 minutes TTL (900 seconds)
        now = int(datetime.now(timezone.utc).timestamp())
        claims = {
            "sub": user["id"],
            "system_role": user["system_role"],
            "tenant_id": active_tenant,
            "tenant_role": caller_role,
            "iat": now,
            "exp": now + 900,
        }
        new_access_token = jwt.encode(claims, JWT_SECRET, algorithm=JWT_ALGORITHM)
        new_refresh_token = f"rotated_rt_{user['id']}_{now}"
        user["active_refresh_tokens"].add(new_refresh_token)

        return self._make_data({
            "access_token": new_access_token,
            "refresh_token": new_refresh_token,
            "token_type": "Bearer",
            "expires_in": 900,
            "tenant_id": active_tenant,
            "tenant_role": caller_role,
        })

    # F05: Gateway Public Resend
    def resend_public_email(self, email: str) -> ResponseWrapper:
        # Public route: no authentication required
        # Idempotent response, does not leak whether email exists
        return self._make_data({"message": "Verification email sent if account exists"})

    # F03: RabbitMQ Event handler
    def emit_event(self, event_type: str, payload: Dict[str, Any]) -> bool:
        if event_type in ("tenant.suspended", "tenant.suspended.v1"):
            tenant_id = payload.get("tenantId") or payload.get("tenant_id")
            if tenant_id and tenant_id in self.tenants:
                self.tenants[tenant_id]["status"] = "SUSPENDED"
                for u in self.users.values():
                    if tenant_id in u.get("memberships", {}):
                        u["memberships"][tenant_id] = "SUSPENDED"
                        # Revoke tokens if user has no other active tenant
                        active_other = [t for t, r in u["memberships"].items() if t != tenant_id and r != "SUSPENDED"]
                        if not active_other and u.get("system_role") != "SYSTEM_ADMIN":
                            for rt in list(u.get("active_refresh_tokens", set())):
                                self.revoked_refresh_tokens.add(rt)
                            u["active_refresh_tokens"].clear()
            return True
        elif event_type == "tenant.approved.v1":
            tenant_id = payload.get("tenantId") or payload.get("tenant_id")
            if tenant_id and tenant_id in self.tenants:
                self.tenants[tenant_id]["status"] = "ACTIVE"
                for u in self.users.values():
                    if tenant_id in u.get("memberships", {}) and u["memberships"][tenant_id] == "PENDING":
                        u["memberships"][tenant_id] = "OWNER"
            return True
        return True

    # F15: Tenant Workflow Protection
    def get_tenant_workflow(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        is_system_admin = claims.get("system_role") == "SYSTEM_ADMIN"
        token_tenant = claims.get("tenant_id")

        if not is_system_admin:
            if not token_tenant or token_tenant != tenant_id:
                # User without tenant or cross-tenant cannot read workflow
                return self._make_error(403, "Forbidden", "Access to tenant workflow denied")

        tenant = self.tenants.get(tenant_id)
        if not tenant:
            return self._make_error(404, "NOT_FOUND", "Tenant not found")

        return self._make_data({
            "tenant_id": tenant_id,
            "status": tenant["status"],
            "workflow_step": "COMPLETED" if tenant["status"] == "ACTIVE" else "PENDING",
        })

    def get_tenant_kyc(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        is_system_admin = claims.get("system_role") == "SYSTEM_ADMIN"
        token_tenant = claims.get("tenant_id")

        if not is_system_admin:
            if not token_tenant or token_tenant != tenant_id:
                return self._make_error(403, "Forbidden", "Access to tenant KYC denied")

        tenant = self.tenants.get(tenant_id)
        if not tenant:
            return self._make_error(404, "NOT_FOUND", "Tenant not found")

        return self._make_data(tenant.get("kyc_data", {}))

    def poll_provisioning(self, tenant_id: str, token: Optional[str] = None) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        tenant = self.tenants.get(tenant_id)
        if not tenant:
            return self._make_error(404, "NOT_FOUND", "Tenant not found")

        # Creator of tenant can poll provisioning even before tenant assignment
        if tenant.get("creator_id") == caller_id or claims.get("system_role") == "SYSTEM_ADMIN":
            return self._make_data({"tenant_id": tenant_id, "provisioning_status": "READY"})

        return self._make_error(403, "Forbidden", "Cannot poll provisioning for foreign tenant")

    # F12: Order Create & Cancel Role Check
    def create_order(
        self, tenant_id: str, order_data: Dict[str, Any], token: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_role = claims.get("tenant_role")
        is_sysadmin = claims.get("system_role") == "SYSTEM_ADMIN"

        # F12 Rule: DRIVER role cannot create orders
        if caller_role == "DRIVER" and not is_sysadmin:
            return self._make_error(403, "Forbidden", "DRIVER role cannot create orders")

        order_id = f"ord-{int(datetime.now().timestamp())}"
        order = {
            "id": order_id,
            "tenant_id": tenant_id,
            "status": "CREATED",
            "assigned_driver_id": None,
            "recipient_name": order_data.get("recipient_name"),
            "cod_amount": order_data.get("cod_amount", 0.0),
        }
        self.orders[order_id] = order
        return self._make_data(order, status=201)

    def cancel_order(
        self, order_id: str, reason: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_role = claims.get("tenant_role")
        is_sysadmin = claims.get("system_role") == "SYSTEM_ADMIN"

        # F12 Rule: DRIVER role cannot cancel orders
        if caller_role == "DRIVER" and not is_sysadmin:
            return self._make_error(403, "Forbidden", "DRIVER role cannot cancel orders")

        order = self.orders.get(order_id)
        if not order:
            return self._make_error(404, "NOT_FOUND", "Order not found")

        order["status"] = "CANCELLED"
        return self._make_data({"id": order_id, "status": "CANCELLED", "reason": reason})

    # F13: Order View Driver Scoping
    def get_order(
        self, order_id: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        caller_role = claims.get("tenant_role")
        caller_tenant = claims.get("tenant_id")
        is_sysadmin = claims.get("system_role") == "SYSTEM_ADMIN"

        order = self.orders.get(order_id)
        if not order:
            return self._make_error(404, "NOT_FOUND", "Order not found")

        # Tenant boundary check
        if not is_sysadmin and order["tenant_id"] != caller_tenant:
            return self._make_error(403, "Forbidden", "Cross-tenant order view denied")

        # F13 Rule: DRIVER can ONLY view orders assigned to them
        if caller_role == "DRIVER" and not is_sysadmin:
            if order.get("assigned_driver_id") != caller_id:
                return self._make_error(403, "Forbidden", "DRIVER can only view assigned orders")

        return self._make_data(order)

    # F14: Report Incident Authorization
    def report_incident(
        self, order_id: str, incident_data: Dict[str, Any], token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        caller_role = claims.get("tenant_role")
        caller_tenant = claims.get("tenant_id")
        is_sysadmin = claims.get("system_role") == "SYSTEM_ADMIN"

        order = self.orders.get(order_id)
        if not order:
            return self._make_error(404, "NOT_FOUND", "Order not found")

        if not is_sysadmin and order["tenant_id"] != caller_tenant:
            return self._make_error(403, "Forbidden", "Cross-tenant incident reporting denied")

        # F14 Rule: Authorized callers are OWNER, TENANT_ADMIN, OPERATOR, SYSTEM_ADMIN, or the assigned DRIVER
        authorized_roles = {"OWNER", "TENANT_ADMIN", "OPERATOR"}
        if not is_sysadmin and caller_role not in authorized_roles:
            if caller_role == "DRIVER":
                if order.get("assigned_driver_id") != caller_id:
                    return self._make_error(403, "Forbidden", "Driver not assigned to this order cannot report incident")
            else:
                return self._make_error(403, "Forbidden", "Role unauthorized to report incident")

        incident = {
            "incident_id": f"inc-{int(datetime.now().timestamp())}",
            "order_id": order_id,
            "incident_type": incident_data.get("incident_type"),
            "description": incident_data.get("description"),
            "reported_by": caller_id,
        }
        self.incidents.append(incident)
        return self._make_data(incident, status=200)

    # F09: Dispatch Plan Authorization
    def acknowledge_plan(
        self, plan_id: str, notes: Optional[str] = None, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        caller_role = claims.get("tenant_role")
        caller_tenant = claims.get("tenant_id")
        is_sysadmin = claims.get("system_role") == "SYSTEM_ADMIN"

        plan = self.dispatch_plans.get(plan_id)
        if not plan:
            return self._make_error(404, "NOT_FOUND", "Dispatch plan not found")

        # Tenant boundary check
        if not is_sysadmin and plan["tenant_id"] != caller_tenant:
            return self._make_error(403, "Forbidden", "Cross-tenant plan acknowledge denied")

        # F09 Rule: Assigned DRIVER or OWNER/SYSTEM_ADMIN can acknowledge
        if not is_sysadmin and caller_role != "OWNER":
            if caller_role != "DRIVER" or plan.get("driver_id") != caller_id:
                return self._make_error(403, "Forbidden", "Only assigned driver or admin can acknowledge plan")

        plan["status"] = "ACKNOWLEDGED"
        return self._make_data({"plan_id": plan_id, "status": "ACKNOWLEDGED", "notes": notes})

    def decline_plan(
        self, plan_id: str, reason: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        caller_role = claims.get("tenant_role")
        caller_tenant = claims.get("tenant_id")
        is_sysadmin = claims.get("system_role") == "SYSTEM_ADMIN"

        plan = self.dispatch_plans.get(plan_id)
        if not plan:
            return self._make_error(404, "NOT_FOUND", "Dispatch plan not found")

        if not is_sysadmin and plan["tenant_id"] != caller_tenant:
            return self._make_error(403, "Forbidden", "Cross-tenant plan decline denied")

        # F09 Rule: Assigned DRIVER or OWNER/SYSTEM_ADMIN can decline
        if not is_sysadmin and caller_role != "OWNER":
            if caller_role != "DRIVER" or plan.get("driver_id") != caller_id:
                return self._make_error(403, "Forbidden", "Only assigned driver or admin can decline plan")

        plan["status"] = "DECLINED"
        return self._make_data({"plan_id": plan_id, "status": "DECLINED", "reason": reason})

    # F10: Driver Plans Tenant Boundary
    def list_driver_plans(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_id = claims.get("sub")
        caller_tenant = claims.get("tenant_id")
        is_sysadmin = claims.get("system_role") == "SYSTEM_ADMIN"

        if not caller_tenant and not is_sysadmin:
            return self._make_error(403, "Forbidden", "Tenant context required")

        tenant = self.tenants.get(caller_tenant)
        if tenant and tenant.get("status") == "SUSPENDED":
            return self._make_data([])

        # Filter strictly by tenant and driver
        results = [
            p for p in self.dispatch_plans.values()
            if (is_sysadmin or p["tenant_id"] == caller_tenant) and (is_sysadmin or p["driver_id"] == caller_id)
        ]
        return self._make_data(results)

    # F11: Simulation No Restrictions for DRIVER
    def get_plan_tracking_stream(self, plan_id: str, token: Optional[str] = None) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")
        return ResponseWrapper(status_code=200, text="data: {\"plan_id\":\"" + plan_id + "\",\"lat\":10.77}\n\n", headers={"Content-Type": "text/event-stream"})

    def start_tracking_simulation(self, plan_id: str, token: Optional[str] = None) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")
        return self._make_data({"plan_id": plan_id, "simulation": "STARTED"})

    def stop_tracking_simulation(self, plan_id: str, token: Optional[str] = None) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")
        return self._make_data({"plan_id": plan_id, "simulation": "STOPPED"})

    def get_driver_simulation_stream(self, token: Optional[str] = None) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")
        return ResponseWrapper(status_code=200, text="data: {\"status\":\"SIMULATION_ACTIVE\"}\n\n", headers={"Content-Type": "text/event-stream"})

    # F16: Settlement Permissions
    def approve_settlement(
        self, settlement_id: str, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_role = claims.get("tenant_role")
        is_sysadmin = claims.get("system_role") == "SYSTEM_ADMIN"

        # F16 Rule: OPERATOR cannot approve settlements
        if caller_role == "OPERATOR" and not is_sysadmin:
            return self._make_error(403, "Forbidden", "OPERATOR role cannot approve settlements")

        allowed = {"OWNER", "TENANT_ADMIN"}
        if not is_sysadmin and caller_role not in allowed:
            return self._make_error(403, "Forbidden", "Role unauthorized to approve settlements")

        settlement = self.settlements.get(settlement_id)
        if not settlement:
            return self._make_error(404, "NOT_FOUND", "Settlement not found")

        settlement["status"] = "APPROVED"
        return self._make_data(settlement)

    def mark_settlement_paid(
        self, settlement_id: str, payout_data: Dict[str, Any], token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        caller_role = claims.get("tenant_role")
        is_sysadmin = claims.get("system_role") == "SYSTEM_ADMIN"

        # F16 Rule: OPERATOR cannot mark settlement paid
        if caller_role == "OPERATOR" and not is_sysadmin:
            return self._make_error(403, "Forbidden", "OPERATOR role cannot mark settlements as paid")

        allowed = {"OWNER", "TENANT_ADMIN"}
        if not is_sysadmin and caller_role not in allowed:
            return self._make_error(403, "Forbidden", "Role unauthorized to mark settlements as paid")

        settlement = self.settlements.get(settlement_id)
        if not settlement:
            return self._make_error(404, "NOT_FOUND", "Settlement not found")

        settlement["status"] = "PAID"
        return self._make_data(settlement)

    def list_settlements(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        # VIEWER is permitted (F16)
        caller_tenant = claims.get("tenant_id")
        results = [s for s in self.settlements.values() if s["tenant_id"] == caller_tenant]
        return self._make_data(results)

    def get_earnings(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Invalid or expired token")

        # VIEWER is permitted (F16)
        return self._make_data({"total_earnings": 5000000.0, "currency": "VND"})

    # F17: Driver Service Internal Key
    def get_all_drivers_internal(
        self, service_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        # F17 Rule: Check service_key
        if not service_key or service_key != self.server_driver_service_key:
            return self._make_error(401, "Unauthorized", "Invalid or empty X-Service-Key")

        # F17 Rule: Reject if tenant_id is missing / null
        if not tenant_id:
            return self._make_error(
                403, "Forbidden", "Internal queries require tenant_id parameter; global dumps prohibited"
            )

        # Return drivers for tenant
        drivers = [
            {"id": u["id"], "email": u["email"], "tenant_id": tenant_id}
            for u in self.users.values()
            if u.get("memberships", {}).get(tenant_id) == "DRIVER"
        ]
        return self._make_data(drivers)

    # F18 & F19: Routing Engine
    def optimize_batch(
        self, batch_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        # F18: Empty server internal key must not allow bypass
        if internal_key is not None:
            if not self.server_routing_internal_key or not self.server_routing_internal_key.strip():
                return self._make_error(401, "Unauthorized", "Internal key authentication disabled or empty")
            if internal_key != self.server_routing_internal_key:
                return self._make_error(401, "Unauthorized", "Invalid internal key")
            return self._make_data({"route_id": "rt-opt-01", "total_distance": 12.5})

        # Token auth
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Authentication required")

        # F19 Rule: VIEWER cannot invoke optimization APIs
        if claims.get("tenant_role") == "VIEWER":
            return self._make_error(403, "Forbidden", "VIEWER cannot directly invoke optimization APIs")

        return self._make_data({
            "route_id": "rt-opt-batch-01",
            "total_distance": 15.2,
            "stops": [{"order_id": IDS.ORDER_1_ID, "seq": 1}],
        })

    def optimize_insert(
        self, insert_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Authentication required")

        if claims.get("tenant_role") == "VIEWER":
            return self._make_error(403, "Forbidden", "VIEWER cannot directly invoke optimization APIs")

        return self._make_data({"route_id": "rt-opt-insert-01", "total_distance": 18.0})

    def optimize_reoptimize(
        self, reoptimize_data: Dict[str, Any], token: Optional[str] = None, internal_key: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Authentication required")

        # DRIVER is allowed (F19 explicit: do not restrict DRIVER)
        if claims.get("tenant_role") == "VIEWER":
            return self._make_error(403, "Forbidden", "VIEWER cannot directly invoke optimization APIs")

        return self._make_data({"route_id": "rt-opt-reopt-01", "total_distance": 14.0})

    def get_routing_plans(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Authentication required")

        # VIEWER can read plans (F19)
        return self._make_data([{"route_id": "rt-opt-batch-01", "status": "OPTIMIZED"}])

    # F20: Notification Sensitive URLs
    def record_log_message(self, message: str) -> None:
        """Emulate logger: redact sensitive tokens before writing."""
        # Redact any URL containing verification tokens, reset tokens, or invite tokens
        redacted = re.sub(
            r"(https?://\S*(?:email/verify|password/reset|verify-email|reset-password|invitations/accept)\S*)",
            "[REDACTED_TOKEN_URL]",
            message,
        )
        self.logged_lines.append(redacted)

    def broadcast_sse_alert(self, payload: Dict[str, Any]) -> None:
        """Broadcast alert over SSE: sanitize any sensitive URL."""
        sanitized = dict(payload)
        for key in ["url", "token_url", "link"]:
            if key in sanitized:
                sanitized[key] = "[REDACTED]"
        self.sse_broadcast_messages.append(sanitized)

    # F21: Notification Viewer Access
    def get_dispatch_alerts(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Authentication required")

        caller_tenant = claims.get("tenant_id")
        if tenant_id and caller_tenant != tenant_id and claims.get("system_role") != "SYSTEM_ADMIN":
            return self._make_error(403, "Forbidden", "Cross-tenant alert read denied")

        # VIEWER is allowed
        results = [a for a in self.alerts if a["tenant_id"] == caller_tenant]
        return self._make_data(results)

    def get_notification_inbox(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Authentication required")

        caller_id = claims.get("sub")
        results = [i for i in self.inbox_items if i.get("user_id") == caller_id]
        return self._make_data(results)

    def get_notification_stream(
        self, token: Optional[str] = None, tenant_id: Optional[str] = None
    ) -> ResponseWrapper:
        claims = self._verify_token(token)
        if not claims:
            return self._make_error(401, "Unauthorized", "Authentication required")

        caller_tenant = claims.get("tenant_id")
        if tenant_id and caller_tenant != tenant_id and claims.get("system_role") != "SYSTEM_ADMIN":
            return self._make_error(403, "Forbidden", "Cross-tenant notification stream denied")

        return ResponseWrapper(status_code=200, text="data: {\"status\":\"CONNECTED\"}\n\n", headers={"Content-Type": "text/event-stream"})
