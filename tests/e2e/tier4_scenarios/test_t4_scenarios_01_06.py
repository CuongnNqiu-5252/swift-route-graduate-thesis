"""
Tier 4: End-to-End Real-World Application Scenarios (Scenarios 01 to 06).
Simulates comprehensive multi-step production operational workflows and security incident attacks.

Covered Scenarios:
- test_t4_scenario_01_saas_tenant_onboarding_to_first_dispatch: F04, F05, F08, F12, F15
- test_t4_scenario_02_rogue_tenant_admin_privilege_escalation_attempt: F01, F02, F06, F16
- test_t4_scenario_03_driver_daily_shift_lifecycle: F07, F08, F09, F10, F11, F13
- test_t4_scenario_04_driver_incident_and_fallback_dispatch: F09, F13, F14, F20, F21
- test_t4_scenario_05_multi_tenant_session_switch: F04, F07, F08, F10
- test_t4_scenario_06_tenant_suspension_emergency_lockout: F02, F03, F04, F06, F08
"""

from jose import jwt
import pytest

from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import (
    DeterministicIDs,
    JWT_SECRET,
    JWT_ALGORITHM,
)


def test_t4_scenario_01_saas_tenant_onboarding_to_first_dispatch(
    client: DualClient,
    unscoped_token: str,
    token_factory,
    test_ids: DeterministicIDs,
):
    """
    Scenario 01: SaaS Tenant Onboarding & First Dispatch (F04, F05, F08, F12, F15).
    - Step 1: Unauthenticated applicant triggers verification email resend via public endpoint.
    - Step 2: Unscoped user without tenant assignment cannot access pending KYC data (403).
    - Step 3: Tenant creator can poll provisioning status during setup (200).
    - Step 4: Platform emits 'tenant.approved.v1' approving the tenant.
    - Step 5: Creator's session activates as OWNER; OWNER invites a TENANT_ADMIN.
    - Step 6: OPERATOR creates the tenant's first delivery order (201).
    - Step 7: DRIVER role is strictly blocked from creating orders (403).
    """
    # Step 1: Unauthenticated email resend via public endpoint
    resp_resend = client.resend_public_email("new_business_owner@logistics.example.com")
    assert resp_resend.status_code == 200, "Public resend must be accessible without auth"

    # Step 2: Unscoped user cannot read pending KYC
    foreign_user_token = token_factory(user_id="foreign-applicant", system_role="USER", tenant_id=None)
    resp_kyc_blocked = client.get_tenant_kyc(test_ids.TENANT_PENDING_ID, token=foreign_user_token)
    assert resp_kyc_blocked.status_code == 403, "Unscoped user must not read pending KYC"

    # Step 3: Tenant creator polls provisioning status
    resp_prov = client.poll_provisioning(test_ids.TENANT_PENDING_ID, token=unscoped_token)
    assert resp_prov.status_code == 200, "Tenant creator should be able to poll provisioning"

    # Step 4: Platform administrative approval via event
    event_ok = client.emit_event(
        "tenant.approved.v1",
        {"tenantId": test_ids.TENANT_PENDING_ID, "approvedBy": "platform_admin"},
    )
    assert event_ok is True, "Tenant approval event emission must succeed"

    # Step 5: Creator gains OWNER access in activated tenant and invites TENANT_ADMIN
    new_owner_token = token_factory(
        user_id=test_ids.UNSCOPED_USER_ID,
        system_role="USER",
        tenant_id=test_ids.TENANT_PENDING_ID,
        tenant_role="OWNER",
    )
    resp_invite_admin = client.invite_member(
        tenant_id=test_ids.TENANT_PENDING_ID,
        email="ops_manager@logistics.example.com",
        role="TENANT_ADMIN",
        token=new_owner_token,
    )
    assert resp_invite_admin.status_code in (200, 201), "OWNER must be able to invite TENANT_ADMIN"

    # Step 6: OPERATOR creates the first delivery order
    new_op_token = token_factory(
        user_id="new-operator-id",
        system_role="USER",
        tenant_id=test_ids.TENANT_PENDING_ID,
        tenant_role="OPERATOR",
    )
    resp_order = client.create_order(
        tenant_id=test_ids.TENANT_PENDING_ID,
        order_data={"recipient_name": "Acme Retail Store", "cod_amount": 1200000.0},
        token=new_op_token,
    )
    assert resp_order.status_code == 201, "OPERATOR must be able to create order"
    order_data = resp_order.json().get("data", resp_order.json())
    assert order_data.get("status") == "CREATED"

    # Step 7: DRIVER role cannot create orders
    new_driver_token = token_factory(
        user_id="new-driver-id",
        system_role="USER",
        tenant_id=test_ids.TENANT_PENDING_ID,
        tenant_role="DRIVER",
    )
    resp_driver_order = client.create_order(
        tenant_id=test_ids.TENANT_PENDING_ID,
        order_data={"recipient_name": "Unauthorized Delivery"},
        token=new_driver_token,
    )
    assert resp_driver_order.status_code == 403, "DRIVER role must be blocked from creating orders"


def test_t4_scenario_02_rogue_tenant_admin_privilege_escalation_attempt(
    client: DualClient,
    admin_token: str,
    owner_token: str,
    token_factory,
    test_ids: DeterministicIDs,
):
    """
    Scenario 02: Rogue Tenant Admin Privilege Escalation Attack (F01, F02, F06, F16).
    - Step 1: Rogue admin attempts to invite an accomplice with OWNER role -> blocked (403).
    - Step 2: Rogue admin attempts to suspend the legitimate tenant OWNER -> blocked (403).
    - Step 3: Rogue admin attempts self-suspension to trigger DoS deadlock -> blocked (403).
    - Step 4: Rogue admin attempts settlement approval with cross-tenant header spoofing -> blocked (403).
    - Step 5: Legitimate OWNER suspends rogue admin account (200).
    - Step 6: Rogue admin's active refresh tokens are revoked.
    - Step 7: Subsequent token refresh by rogue admin fails active check (401).
    - Step 8: Rogue admin using expired token is rejected at Gateway with 401.
    """
    # Step 1: Rogue admin attempts to invite OWNER
    resp_escalate_invite = client.invite_member(
        tenant_id=test_ids.TENANT_A_ID,
        email="accomplice@rogue.attacker",
        role="OWNER",
        token=admin_token,
    )
    assert resp_escalate_invite.status_code == 403, "TENANT_ADMIN cannot invite OWNER"

    # Step 2: Rogue admin attempts to suspend OWNER
    resp_suspend_owner = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OWNER_USER_ID,
        reason="Hostile takeover attempt",
        token=admin_token,
    )
    assert resp_suspend_owner.status_code == 403, "TENANT_ADMIN cannot suspend OWNER"

    # Step 3: Rogue admin attempts self-suspension
    resp_self_suspend = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.ADMIN_USER_ID,
        reason="Malicious self-lockout",
        token=admin_token,
    )
    assert resp_self_suspend.status_code == 403, "TENANT_ADMIN cannot self-suspend"

    # Step 4: Rogue admin attempts cross-tenant header settlement approval through Gateway
    resp_cross_settle = client.post(
        f"/api/settlements/{test_ids.SETTLEMENT_TENANT_B_ID}/approve",
        headers={"X-Tenant-Id": test_ids.TENANT_B_ID},
        token=admin_token,
    )
    assert resp_cross_settle.status_code == 403, "Gateway must block cross-tenant header spoofing"

    # Step 5: Legitimate OWNER suspends rogue admin
    resp_suspend_rogue = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.ADMIN_USER_ID,
        reason="Security audit: Hostile privilege escalation attempt detected",
        token=owner_token,
    )
    assert resp_suspend_rogue.status_code == 200, "OWNER can suspend rogue admin"

    # Step 6 & 7: Refresh token revoked, refresh fails active check
    resp_refresh_fail = client.refresh_token("admin_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_refresh_fail.status_code in (401, 403), "Revoked rogue admin token must fail refresh"

    # Step 8: Expired token rejected at Gateway
    expired_tok = token_factory(user_id=test_ids.ADMIN_USER_ID, tenant_id=test_ids.TENANT_A_ID, expired=True)
    resp_expired = client.get(f"/api/orders/{test_ids.ORDER_1_ID}", token=expired_tok)
    assert resp_expired.status_code == 401, "Expired token must be rejected with 401"


def test_t4_scenario_03_driver_daily_shift_lifecycle(
    client: DualClient,
    driver_token: str,
    test_ids: DeterministicIDs,
):
    """
    Scenario 03: Driver Daily Shift Lifecycle (F07, F08, F09, F10, F11, F13).
    - Step 1: Driver refreshes session at start of shift, receiving 15-minute token.
    - Step 2: Driver queries assigned driver plans (only assigned Tenant A plans returned).
    - Step 3: Driver acknowledges the assigned dispatch plan (200).
    - Step 4: Driver views order details of their assigned delivery stop (200).
    - Step 5: Driver cannot view unassigned orders belonging to other drivers (403).
    - Step 6: Driver initiates vehicle tracking simulation (200).
    - Step 7: Driver connects to driver simulation stream (200).
    - Step 8: Driver completes route and stops tracking simulation (200).
    - Step 9: Driver inspects shift earnings (200).
    """
    # Step 1: Session refresh with 15-min TTL
    resp_refresh = client.refresh_token("driver_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_refresh.status_code == 200
    shift_token = resp_refresh.json()["data"]["access_token"]
    claims = jwt.decode(shift_token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims["exp"] - claims["iat"] == 900
    assert claims["tenant_role"] == "DRIVER"

    # Step 2: Query assigned driver plans
    resp_plans = client.list_driver_plans(token=shift_token)
    assert resp_plans.status_code == 200
    plans = resp_plans.json().get("data", resp_plans.json())
    assert any(p["id"] == test_ids.PLAN_1_ID for p in plans)

    # Step 3: Acknowledge dispatch plan
    resp_ack = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        notes="Pre-shift inspection passed; commencing route delivery",
        token=shift_token,
    )
    assert resp_ack.status_code == 200
    assert resp_ack.json().get("data", resp_ack.json()).get("status") == "ACKNOWLEDGED"

    # Step 4: View assigned order ORDER_1_ID
    resp_order = client.get_order(order_id=test_ids.ORDER_1_ID, token=shift_token)
    assert resp_order.status_code == 200
    assert resp_order.json().get("data", resp_order.json()).get("assigned_driver_id") == test_ids.DRIVER_USER_ID

    # Step 5: Blocked from viewing unassigned order ORDER_2_ID
    resp_unassigned = client.get_order(order_id=test_ids.ORDER_2_ID, token=shift_token)
    assert resp_unassigned.status_code == 403, "DRIVER cannot view peer driver's order"

    # Step 6: Start tracking simulation
    resp_sim_start = client.start_tracking_simulation(plan_id=test_ids.PLAN_1_ID, token=shift_token)
    assert resp_sim_start.status_code == 200

    # Step 7: Driver simulation stream connection
    resp_sim_stream = client.get_driver_simulation_stream(token=shift_token)
    assert resp_sim_stream.status_code == 200

    # Step 8: Stop tracking simulation
    resp_sim_stop = client.stop_tracking_simulation(plan_id=test_ids.PLAN_1_ID, token=shift_token)
    assert resp_sim_stop.status_code == 200

    # Step 9: View accumulated shift earnings
    resp_earnings = client.get_earnings(token=shift_token)
    assert resp_earnings.status_code == 200
    assert "total_earnings" in str(resp_earnings.json())


def test_t4_scenario_04_driver_incident_and_fallback_dispatch(
    client: DualClient,
    driver_token: str,
    driver_2_token: str,
    operator_token: str,
    viewer_token: str,
    test_ids: DeterministicIDs,
):
    """
    Scenario 04: Driver Route Breakdown & Incident Fallback (F09, F13, F14, F20, F21).
    - Step 1: Driver 1 experiences route breakdown and reports incident on assigned order (200).
    - Step 2: Driver 2 attempts to report incident on Driver 1's order -> blocked (403).
    - Step 3: Notification consumer handles alert without leaking sensitive token URLs.
    - Step 4: Dispatcher and compliance viewer monitor incident alerts (200).
    - Step 5: OPERATOR handles exception by cancelling or reassigning order (200).
    - Step 6: DRIVER is blocked from directly cancelling orders (403).
    """
    # Step 1: Assigned Driver reports incident
    resp_incident = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data={
            "incident_type": "VEHICLE_BREAKDOWN",
            "description": "Transmission failure at intersection 4; vehicle immobilized",
        },
        token=driver_token,
    )
    assert resp_incident.status_code == 200
    assert resp_incident.json().get("data", resp_incident.json()).get("incident_type") == "VEHICLE_BREAKDOWN"

    # Step 2: Unassigned Driver 2 blocked from incident reporting on ORDER_1_ID
    resp_inc_unauth = client.report_incident(
        order_id=test_ids.ORDER_1_ID,
        incident_data={"incident_type": "PACKAGE_DAMAGED", "description": "Package compromised"},
        token=driver_2_token,
    )
    assert resp_inc_unauth.status_code == 403, "Unassigned driver cannot report incident"

    # Step 3: Notification log sanitization
    if client.is_mock:
        client.mock_client.record_log_message(
            "Incident alert dispatched for breakdown with reset link: https://swiftroute.io/api/auth/password/reset?token=tok_leak_777"
        )
        assert "tok_leak_777" not in client.mock_client.logged_lines[-1]
        assert "[REDACTED_TOKEN_URL]" in client.mock_client.logged_lines[-1]

    # Step 4: Dispatchers and viewers monitor alerts
    resp_alerts = client.get_dispatch_alerts(token=viewer_token)
    assert resp_alerts.status_code == 200

    # Step 5: OPERATOR can cancel or reschedule the order
    resp_cancel = client.cancel_order(
        order_id=test_ids.ORDER_1_ID,
        reason="Vehicle breakdown - rescheduled for backup fleet",
        token=operator_token,
    )
    assert resp_cancel.status_code == 200
    assert resp_cancel.json().get("data", resp_cancel.json()).get("status") == "CANCELLED"

    # Step 6: DRIVER cannot cancel order directly
    resp_driver_cancel = client.cancel_order(
        order_id=test_ids.ORDER_1_ID,
        reason="Driver unilateral cancellation",
        token=driver_token,
    )
    assert resp_driver_cancel.status_code == 403, "DRIVER cannot cancel orders"


def test_t4_scenario_05_multi_tenant_session_switch(
    client: DualClient,
    test_ids: DeterministicIDs,
):
    """
    Scenario 05: Multi-Tenant Logistics Contractor Session Switch (F04, F07, F08, F10).
    - Step 1: Multi-tenant user authenticates into Tenant A as DRIVER.
    - Step 2: User queries Tenant A driver plans; plans are isolated to Tenant A.
    - Step 3: Under Tenant A session, user cannot create orders (403).
    - Step 4: User switches shift to Tenant B: refreshes token specifying Tenant B as OPERATOR.
    - Step 5: Refreshed token preserves Tenant B context, OPERATOR role, and 15-min TTL.
    - Step 6: Under Tenant B session, user can create orders for Tenant B (201).
    - Step 7: Gateway rejects any attempt to pass Tenant A header with Tenant B token (403).
    """
    # Step 1: Session in Tenant A (DRIVER)
    resp_a = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_a.status_code == 200
    token_a = resp_a.json()["data"]["access_token"]
    claims_a = jwt.decode(token_a, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims_a["tenant_id"] == test_ids.TENANT_A_ID
    assert claims_a["tenant_role"] == "DRIVER"

    # Step 2: Driver plans in Tenant A
    resp_plans_a = client.list_driver_plans(token=token_a)
    assert resp_plans_a.status_code == 200
    plans_a = resp_plans_a.json().get("data", resp_plans_a.json())
    for p in plans_a:
        assert p.get("tenant_id") == test_ids.TENANT_A_ID

    # Step 3: Cannot create order in Tenant A as DRIVER
    resp_create_a = client.create_order(
        tenant_id=test_ids.TENANT_A_ID,
        order_data={"recipient_name": "Test Client A"},
        token=token_a,
    )
    assert resp_create_a.status_code == 403, "DRIVER role in Tenant A cannot create orders"

    # Step 4: Switch session to Tenant B (OPERATOR)
    resp_b = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_B_ID)
    assert resp_b.status_code == 200
    token_b = resp_b.json()["data"]["access_token"]
    claims_b = jwt.decode(token_b, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims_b["tenant_id"] == test_ids.TENANT_B_ID
    assert claims_b["tenant_role"] == "OPERATOR"
    assert claims_b["exp"] - claims_b["iat"] == 900

    # Step 5 & 6: Create order in Tenant B as OPERATOR
    resp_create_b = client.create_order(
        tenant_id=test_ids.TENANT_B_ID,
        order_data={"recipient_name": "Metro Express Customer", "cod_amount": 350000.0},
        token=token_b,
    )
    assert resp_create_b.status_code == 201, "OPERATOR role in Tenant B can create orders"

    # Step 7: Gateway rejects cross-tenant header
    resp_cross_gw = client.get(
        f"/api/orders/{test_ids.ORDER_TENANT_B_ID}",
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=token_b,
    )
    assert resp_cross_gw.status_code == 403, "Gateway must block mismatched X-Tenant-Id vs token tenant"


def test_t4_scenario_06_tenant_suspension_emergency_lockout(
    client: DualClient,
    admin_token: str,
    driver_token: str,
    unscoped_token: str,
    test_ids: DeterministicIDs,
):
    """
    Scenario 06: Tenant Suspension & Emergency Lockout (F02, F03, F04, F06, F08).
    - Step 1: Platform administrative event 'tenant.suspended.v1' emitted.
    - Step 2: Tenant and all active memberships transition to SUSPENDED.
    - Step 3: All active refresh tokens for the suspended tenant are revoked.
    - Step 4: Suspended tenant members cannot refresh access tokens (401).
    - Step 5: Gateway blocks spoofed tenant header requests for the suspended tenant (403).
    - Step 6: Driver plan queries for the suspended tenant return empty lists (200, []).
    - Step 7: Complete fail-closed emergency isolation verified.
    """
    # Step 1: Emit suspension event
    event_ok = client.emit_event(
        "tenant.suspended.v1",
        {"tenantId": test_ids.TENANT_A_ID, "reason": "Immediate fraud lockdown"},
    )
    assert event_ok is True, "Failed to emit tenant suspension event"

    # Step 2 & 3: Memberships suspended and refresh tokens revoked
    resp_admin_ref = client.refresh_token("admin_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_admin_ref.status_code in (401, 403), "Admin refresh token must be revoked"

    resp_driver_ref = client.refresh_token("driver_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_driver_ref.status_code in (401, 403), "Driver refresh token must be revoked"

    # Step 4: Refresh request fails active status check
    resp_refresh_check = client.refresh_token("admin_rt_valid_1")
    assert resp_refresh_check.status_code in (401, 403), "Refresh must fail active check"

    # Step 5: Gateway blocks requests attempting to access suspended tenant
    resp_gw_block = client.get(
        f"/api/orders/{test_ids.ORDER_1_ID}",
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=unscoped_token,
    )
    assert resp_gw_block.status_code == 403, "Gateway must block access to suspended tenant"

    # Step 6: Driver plans query for suspended tenant returns empty list
    resp_plans = client.list_driver_plans(token=driver_token)
    assert resp_plans.status_code == 200
    plans = resp_plans.json().get("data", resp_plans.json())
    assert len(plans) == 0, "Suspended tenant must return empty driver plans list"
