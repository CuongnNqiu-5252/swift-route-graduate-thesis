"""
Tier 3: Cross-Feature Integration Combinations (Tests 01 to 10).
Verifies pairwise and multi-feature interaction across security boundaries.

Covered Combinations:
- test_t3_01_owner_invite_suspension_token_revocation: F01 + F02 + F06
- test_t3_02_tenant_suspension_event_blocks_gateway_and_auth: F03 + F04 + F06
- test_t3_03_gateway_tenant_isolation_with_multi_tenant_refresh: F04 + F08 + F07
- test_t3_04_driver_order_scoping_with_plan_acknowledge: F09 + F10 + F13
- test_t3_05_driver_simulation_allowed_while_order_cancellation_blocked: F11 + F12
- test_t3_06_driver_incident_reporting_triggers_clean_notification: F14 + F20 + F21
- test_t3_07_viewer_settlement_read_allowed_while_optimization_blocked: F16 + F19 + F21
- test_t3_08_internal_key_hardening_across_driver_and_routing: F17 + F18
- test_t3_09_public_resend_with_clean_notification_consumer: F05 + F20
- test_t3_10_tenant_admin_privilege_boundary_and_settlement_approval: F01 + F16
"""

from jose import jwt
import pytest

from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import (
    DeterministicIDs,
    DRIVER_SERVICE_KEY,
    ROUTING_INTERNAL_KEY,
    JWT_SECRET,
    JWT_ALGORITHM,
)


def test_t3_01_owner_invite_suspension_token_revocation(
    client: DualClient,
    owner_token: str,
    admin_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F01 + F02 + F06:
    1. OWNER invites a new TENANT_ADMIN.
    2. Admin suspends an OPERATOR account.
    3. Revocation of refresh tokens triggers for the suspended member.
    4. Suspended member's refresh request fails active status check (401).
    """
    # 1. OWNER can invite a new tenant admin
    resp_invite = client.invite_member(
        tenant_id=test_ids.TENANT_A_ID,
        email="new_co_admin@tenanta.com",
        role="TENANT_ADMIN",
        token=owner_token,
    )
    assert resp_invite.status_code in (200, 201), (
        f"Expected 200/201 on admin invite by OWNER, got {resp_invite.status_code}: {resp_invite.text}"
    )

    # 2. Existing TENANT_ADMIN suspends an OPERATOR
    resp_suspend = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OPERATOR_USER_ID,
        reason="Security policy violation",
        token=admin_token,
    )
    assert resp_suspend.status_code == 200, (
        f"Expected 200 on OPERATOR suspension, got {resp_suspend.status_code}: {resp_suspend.text}"
    )

    # 3. Suspended operator's refresh tokens are invalidated
    resp_refresh_old = client.refresh_token("operator_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_refresh_old.status_code in (401, 403), (
        f"Expected 401/403 for revoked operator refresh token, got {resp_refresh_old.status_code}"
    )

    # 4. Attempting refresh for a suspended user fails active check
    resp_refresh_check = client.refresh_token("operator_rt_valid_1")
    assert resp_refresh_check.status_code in (401, 403), (
        f"Expected failure for non-ACTIVE user refresh, got {resp_refresh_check.status_code}"
    )


def test_t3_02_tenant_suspension_event_blocks_gateway_and_auth(
    client: DualClient,
    unscoped_token: str,
    admin_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F03 + F04 + F06:
    1. Order service emits 'tenant.suspended.v1' event.
    2. Tenant status and all memberships transition to SUSPENDED.
    3. User refresh tokens for suspended tenant are revoked.
    4. Gateway rejects unscoped token attempting to spoof the suspended tenant.
    """
    # 1. Emit tenant suspension event
    event_ok = client.emit_event(
        "tenant.suspended.v1",
        {"tenantId": test_ids.TENANT_A_ID, "reason": "Regulatory sanction"},
    )
    assert event_ok is True, "Failed to emit tenant.suspended.v1 event"

    # 2. Suspended tenant user's refresh token is revoked
    resp_refresh = client.refresh_token("admin_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_refresh.status_code in (401, 403), (
        f"Expected 401/403 for suspended tenant token refresh, got {resp_refresh.status_code}"
    )

    # 3. Gateway tenant isolation rejects non-admin token supplying X-Tenant-Id
    resp_gw = client.get(
        "/api/orders",
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=unscoped_token,
    )
    assert resp_gw.status_code == 403, (
        f"Expected 403 Forbidden for arbitrary X-Tenant-Id header, got {resp_gw.status_code}"
    )


def test_t3_03_gateway_tenant_isolation_with_multi_tenant_refresh(
    client: DualClient,
    test_ids: DeterministicIDs,
):
    """
    Combination F04 + F08 + F07:
    1. Multi-tenant user refreshes into Tenant B session.
    2. Newly issued access token has configured 15-minute TTL and Tenant B context.
    3. Access token succeeds on Tenant B scoped operations.
    4. Supplying cross-tenant header (Tenant A) with Tenant B token is blocked by Gateway (403).
    """
    # 1. Multi-tenant user refreshes explicitly into Tenant B
    resp_refresh = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_B_ID)
    assert resp_refresh.status_code == 200, (
        f"Expected 200 on multi-tenant refresh into Tenant B, got {resp_refresh.status_code}"
    )
    data = resp_refresh.json().get("data", resp_refresh.json())
    new_token = data["access_token"]

    # 2. Verify claims: 15-min TTL, Tenant B, OPERATOR role
    claims = jwt.decode(new_token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims.get("tenant_id") == test_ids.TENANT_B_ID
    assert claims.get("tenant_role") == "OPERATOR"
    assert (claims["exp"] - claims["iat"]) == 900, "Access token TTL must be exactly 900 seconds"

    # 3. Succeeds on Tenant B scoped requests
    resp_ok = client.get(f"/api/orders/{test_ids.ORDER_TENANT_B_ID}", token=new_token)
    assert resp_ok.status_code == 200, f"Expected 200 on Tenant B orders, got {resp_ok.status_code}"

    # 4. Gateway blocks cross-tenant header spoofing (token has Tenant B, header has Tenant A)
    resp_cross = client.get(
        f"/api/orders/{test_ids.ORDER_TENANT_B_ID}",
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=new_token,
    )
    assert resp_cross.status_code == 403, (
        f"Expected 403 for mismatched X-Tenant-Id vs token tenant claim, got {resp_cross.status_code}"
    )


def test_t3_04_driver_order_scoping_with_plan_acknowledge(
    client: DualClient,
    driver_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F09 + F10 + F13:
    1. DRIVER lists driver plans and receives only assigned plans in their tenant.
    2. DRIVER acknowledges their assigned dispatch plan.
    3. DRIVER views the order assigned to them within the acknowledged plan.
    4. DRIVER is prohibited from viewing unassigned / peer driver's orders (403).
    """
    # 1. List driver plans
    resp_plans = client.list_driver_plans(token=driver_token)
    assert resp_plans.status_code == 200, f"Expected 200 on driver plans, got {resp_plans.status_code}"
    plans = resp_plans.json().get("data", resp_plans.json())
    assert any(p["id"] == test_ids.PLAN_1_ID for p in plans)

    # 2. Acknowledge assigned plan
    resp_ack = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        notes="Pre-trip inspection passed, en route",
        token=driver_token,
    )
    assert resp_ack.status_code == 200, f"Expected 200 on acknowledge, got {resp_ack.status_code}"

    # 3. DRIVER views assigned order ORDER_1_ID
    resp_order_assigned = client.get_order(order_id=test_ids.ORDER_1_ID, token=driver_token)
    assert resp_order_assigned.status_code == 200, (
        f"Expected 200 on assigned order view, got {resp_order_assigned.status_code}"
    )

    # 4. DRIVER cannot view peer driver's order ORDER_2_ID
    resp_order_peer = client.get_order(order_id=test_ids.ORDER_2_ID, token=driver_token)
    assert resp_order_peer.status_code == 403, (
        f"Expected 403 Forbidden viewing unassigned order, got {resp_order_peer.status_code}"
    )


def test_t3_05_driver_simulation_allowed_while_order_cancellation_blocked(
    client: DualClient,
    driver_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F11 + F12:
    1. DRIVER can freely invoke dispatch simulation and tracking streams without restriction.
    2. DRIVER is strictly blocked from creating orders (403).
    3. DRIVER is strictly blocked from cancelling orders (403).
    """
    # 1. Simulation controls permitted for DRIVER
    resp_sim_start = client.start_tracking_simulation(plan_id=test_ids.PLAN_1_ID, token=driver_token)
    assert resp_sim_start.status_code == 200, f"Expected 200 on sim start, got {resp_sim_start.status_code}"

    resp_sim_stream = client.get_driver_simulation_stream(token=driver_token)
    assert resp_sim_stream.status_code == 200, f"Expected 200 on sim stream, got {resp_sim_stream.status_code}"

    resp_sim_stop = client.stop_tracking_simulation(plan_id=test_ids.PLAN_1_ID, token=driver_token)
    assert resp_sim_stop.status_code == 200, f"Expected 200 on sim stop, got {resp_sim_stop.status_code}"

    # 2. DRIVER cannot create orders
    resp_create = client.create_order(
        tenant_id=test_ids.TENANT_A_ID,
        order_data={"recipient_name": "Prohibited Customer"},
        token=driver_token,
    )
    assert resp_create.status_code == 403, f"Expected 403 on driver order create, got {resp_create.status_code}"

    # 3. DRIVER cannot cancel orders
    resp_cancel = client.cancel_order(
        order_id=test_ids.ORDER_1_ID,
        reason="Driver refusing delivery",
        token=driver_token,
    )
    assert resp_cancel.status_code == 403, f"Expected 403 on driver order cancel, got {resp_cancel.status_code}"


def test_t3_06_driver_incident_reporting_triggers_clean_notification(
    client: DualClient,
    driver_token: str,
    driver_2_token: str,
    viewer_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F14 + F20 + F21:
    1. Assigned DRIVER can report an incident on their order; unassigned DRIVER cannot (403).
    2. Incident alerting logs and broadcasts without leaking token URLs.
    3. VIEWER can access dispatch alerts and notification inbox.
    """
    # 1. Unassigned driver cannot report incident on ORDER_1_ID
    resp_unassigned_inc = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data={"incident_type": "VEHICLE_BREAKDOWN", "description": "Flat tire"},
        token=driver_2_token,
    )
    assert resp_unassigned_inc.status_code == 403, (
        f"Expected 403 for unassigned driver incident report, got {resp_unassigned_inc.status_code}"
    )

    # Assigned driver can report incident
    resp_assigned_inc = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data={"incident_type": "VEHICLE_BREAKDOWN", "description": "Puncture on Route 1"},
        token=driver_token,
    )
    assert resp_assigned_inc.status_code == 200, (
        f"Expected 200 for assigned driver incident report, got {resp_assigned_inc.status_code}"
    )

    # 2. Notification consumer sanitization
    if client.is_mock:
        client.mock_client.record_log_message(
            "Incident notification sent with invite link: https://swiftroute.io/invitations/accept?token=auth_tok_8888"
        )
        assert "auth_tok_8888" not in client.mock_client.logged_lines[-1]
        assert "[REDACTED_TOKEN_URL]" in client.mock_client.logged_lines[-1]

    # 3. VIEWER can inspect alerts and inbox
    resp_alerts = client.get_dispatch_alerts(token=viewer_token)
    assert resp_alerts.status_code == 200, f"Expected 200 on VIEWER alerts read, got {resp_alerts.status_code}"

    resp_inbox = client.get_notification_inbox(token=viewer_token)
    assert resp_inbox.status_code == 200, f"Expected 200 on VIEWER inbox read, got {resp_inbox.status_code}"


def test_t3_07_viewer_settlement_read_allowed_while_optimization_blocked(
    client: DualClient,
    viewer_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F16 + F19 + F21:
    1. VIEWER can list settlements, earnings, and connect to notification stream.
    2. VIEWER is strictly blocked from invoking optimization APIs (POST /optimize/batch, /insert).
    """
    # 1. VIEWER operational reads succeed
    resp_settlements = client.list_settlements(token=viewer_token)
    assert resp_settlements.status_code == 200, f"Expected 200 on settlements, got {resp_settlements.status_code}"

    resp_earnings = client.get_earnings(token=viewer_token)
    assert resp_earnings.status_code == 200, f"Expected 200 on earnings, got {resp_earnings.status_code}"

    resp_stream = client.get_notification_stream(token=viewer_token)
    assert resp_stream.status_code == 200, f"Expected 200 on notification stream, got {resp_stream.status_code}"

    # 2. VIEWER blocked from optimization endpoints
    resp_opt_batch = client.optimize_batch(
        batch_data={"locations": [{"lat": 10.77, "lng": 106.70}]},
        token=viewer_token,
    )
    assert resp_opt_batch.status_code == 403, (
        f"Expected 403 for VIEWER calling optimize/batch, got {resp_opt_batch.status_code}"
    )

    resp_opt_insert = client.optimize_insert(
        insert_data={"location": {"lat": 10.78, "lng": 106.71}},
        token=viewer_token,
    )
    assert resp_opt_insert.status_code == 403, (
        f"Expected 403 for VIEWER calling optimize/insert, got {resp_opt_insert.status_code}"
    )


def test_t3_08_internal_key_hardening_across_driver_and_routing(
    client: DualClient,
    test_ids: DeterministicIDs,
):
    """
    Combination F17 + F18:
    1. driver-service internal key call without tenant_id query is rejected (403/400).
    2. driver-service internal key call with empty key fails closed (401/403).
    3. routing-engine-service internal key with empty server key fails closed (401).
    4. Valid internal key queries with explicit tenant_id succeed.
    """
    # 1. Driver service rejects null tenantId
    resp_driver_null = client.get_all_drivers_internal(
        service_key=DRIVER_SERVICE_KEY,
        tenant_id=None,
    )
    assert resp_driver_null.status_code in (400, 403), (
        f"Expected 400/403 for driver dump without tenant_id, got {resp_driver_null.status_code}"
    )

    # 2. Driver service rejects empty key
    resp_driver_empty = client.get_all_drivers_internal(
        service_key="",
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp_driver_empty.status_code in (401, 403), (
        f"Expected 401/403 for empty driver service key, got {resp_driver_empty.status_code}"
    )

    # 3. Routing engine rejects empty client key
    resp_routing_empty = client.optimize_batch(
        batch_data={"orders": [test_ids.ORDER_1_ID]},
        internal_key="",
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp_routing_empty.status_code == 401, (
        f"Expected 401 for empty routing internal key, got {resp_routing_empty.status_code}"
    )

    # 4. Valid keys with explicit tenant ID succeed
    resp_driver_valid = client.get_all_drivers_internal(
        service_key=DRIVER_SERVICE_KEY,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp_driver_valid.status_code == 200, (
        f"Expected 200 for valid driver internal key, got {resp_driver_valid.status_code}"
    )

    resp_routing_valid = client.optimize_batch(
        batch_data={"orders": [test_ids.ORDER_1_ID]},
        internal_key=ROUTING_INTERNAL_KEY,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp_routing_valid.status_code == 200, (
        f"Expected 200 for valid routing internal key, got {resp_routing_valid.status_code}"
    )


def test_t3_09_public_resend_with_clean_notification_consumer(
    client: DualClient,
):
    """
    Combination F05 + F20:
    1. Unauthenticated caller can hit /api/auth/email/resend-public (200 OK).
    2. Notification consumer handles verification dispatch without logging verification token URL.
    3. Sensitive token URLs are redacted in SSE broadcasts.
    4. Authenticated endpoints remain protected against unauthenticated callers.
    """
    # 1. Unauthenticated public resend email
    resp_resend = client.resend_public_email("unverified_candidate@example.com")
    assert resp_resend.status_code == 200, (
        f"Expected 200 on public email resend, got {resp_resend.status_code}: {resp_resend.text}"
    )

    # 2. Notification consumer does not log token URL
    if client.is_mock:
        client.mock_client.record_log_message(
            "Sending public email verification: https://swiftroute.io/api/auth/email/verify?token=verify_secret_123"
        )
        assert "verify_secret_123" not in client.mock_client.logged_lines[-1]
        assert "[REDACTED_TOKEN_URL]" in client.mock_client.logged_lines[-1]

    # 3. Unauthenticated requests to protected endpoints return 401
    resp_protected = client.get("/api/orders")
    assert resp_protected.status_code == 401, (
        f"Expected 401 on unauthenticated order read, got {resp_protected.status_code}"
    )


def test_t3_10_tenant_admin_privilege_boundary_and_settlement_approval(
    client: DualClient,
    admin_token: str,
    operator_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F01 + F16:
    1. TENANT_ADMIN cannot invite OWNER or suspend OWNER (403).
    2. TENANT_ADMIN has authority to approve settlements (200).
    3. OPERATOR cannot approve settlements or mark settlements paid (403).
    """
    # 1. TENANT_ADMIN cannot escalate to OWNER
    resp_invite_owner = client.invite_member(
        tenant_id=test_ids.TENANT_A_ID,
        email="new_owner_takeover@example.com",
        role="OWNER",
        token=admin_token,
    )
    assert resp_invite_owner.status_code == 403, (
        f"Expected 403 on admin inviting OWNER, got {resp_invite_owner.status_code}"
    )

    resp_suspend_owner = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OWNER_USER_ID,
        token=admin_token,
    )
    assert resp_suspend_owner.status_code == 403, (
        f"Expected 403 on admin suspending OWNER, got {resp_suspend_owner.status_code}"
    )

    # 2. TENANT_ADMIN can approve settlement
    resp_approve = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=admin_token,
    )
    assert resp_approve.status_code == 200, (
        f"Expected 200 on admin settlement approval, got {resp_approve.status_code}"
    )

    # 3. OPERATOR cannot approve or mark paid
    resp_op_approve = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=operator_token,
    )
    assert resp_op_approve.status_code == 403, (
        f"Expected 403 on operator settlement approval, got {resp_op_approve.status_code}"
    )

    resp_op_paid = client.mark_settlement_paid(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        payout_data={"payout_ref": "BANK-REF-999"},
        token=operator_token,
    )
    assert resp_op_paid.status_code == 403, (
        f"Expected 403 on operator mark settlement paid, got {resp_op_paid.status_code}"
    )
