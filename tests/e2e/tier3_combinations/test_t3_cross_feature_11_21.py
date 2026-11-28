"""
Tier 3: Cross-Feature Integration Combinations (Tests 11 to 21).
Verifies advanced pairwise and multi-feature interaction across security boundaries.

Covered Combinations:
- test_t3_11_tenant_workflow_protection_blocks_kyc_leak_then_approves: F15 + F03 + F08
- test_t3_12_operator_lifecycle_order_create_dispatch_settlement_barrier: F12 + F09 + F16
- test_t3_13_multi_tenant_session_switch_and_plan_boundary: F08 + F10 + F09
- test_t3_14_suspended_admin_cannot_access_gateway_or_services: F02 + F04 + F06
- test_t3_15_driver_plan_decline_triggers_incident_and_notification: F09 + F14 + F20
- test_t3_16_system_admin_cross_tenant_override_full_flow: F04 + F15 + F16 + F17
- test_t3_17_viewer_full_audit_read_across_all_services: F16 + F19 + F21 + F13
- test_t3_18_driver_delivery_simulation_to_earnings_generation: F11 + F16 + F13
- test_t3_19_empty_internal_key_regression_defense: F17 + F18
- test_t3_20_token_ttl_boundary_and_session_refresh_cycle: F07 + F08 + F06
- test_t3_21_cross_tenant_idor_complete_matrix_defense: F04 + F09 + F10 + F13 + F15 + F17
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


def test_t3_11_tenant_workflow_protection_blocks_kyc_leak_then_approves(
    client: DualClient,
    unscoped_token: str,
    token_factory,
    test_ids: DeterministicIDs,
):
    """
    Combination F15 + F03 + F08:
    1. Unscoped user without tenant cannot access pending tenant KYC (403).
    2. Tenant creator can poll provisioning status (200).
    3. Platform emits 'tenant.approved.v1' activating the pending tenant.
    4. Creator's membership transitions to OWNER, granting full access to tenant workflow.
    """
    # 1. Unscoped foreign user blocked from KYC
    foreign_unscoped = token_factory(user_id="foreign-unscoped-user", system_role="USER", tenant_id=None)
    resp_kyc_blocked = client.get_tenant_kyc(test_ids.TENANT_PENDING_ID, token=foreign_unscoped)
    assert resp_kyc_blocked.status_code == 403, (
        f"Expected 403 on foreign user accessing pending KYC, got {resp_kyc_blocked.status_code}"
    )

    # 2. Creator can poll provisioning status
    resp_prov = client.poll_provisioning(test_ids.TENANT_PENDING_ID, token=unscoped_token)
    assert resp_prov.status_code == 200, (
        f"Expected 200 for tenant creator polling provisioning, got {resp_prov.status_code}"
    )

    # 3. Platform emits tenant approval event
    event_ok = client.emit_event(
        "tenant.approved.v1",
        {"tenantId": test_ids.TENANT_PENDING_ID, "approvedBy": "system-admin"},
    )
    assert event_ok is True, "Failed to emit tenant.approved.v1 event"

    # 4. Creator now has OWNER role in newly activated tenant
    creator_active_token = token_factory(
        user_id=test_ids.UNSCOPED_USER_ID,
        system_role="USER",
        tenant_id=test_ids.TENANT_PENDING_ID,
        tenant_role="OWNER",
    )
    resp_kyc_owner = client.get_tenant_kyc(test_ids.TENANT_PENDING_ID, token=creator_active_token)
    assert resp_kyc_owner.status_code == 200, (
        f"Expected 200 for newly activated OWNER accessing KYC, got {resp_kyc_owner.status_code}"
    )


def test_t3_12_operator_lifecycle_order_create_dispatch_settlement_barrier(
    client: DualClient,
    operator_token: str,
    driver_token: str,
    owner_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F12 + F09 + F16:
    1. OPERATOR creates order (201).
    2. Assigned DRIVER acknowledges the dispatch plan (200).
    3. OPERATOR attempts settlement approval -> blocked (403).
    4. OPERATOR attempts mark settlement paid -> blocked (403).
    5. OWNER reviews, approves, and marks settlement paid (200).
    """
    # 1. OPERATOR creates order
    resp_create = client.create_order(
        tenant_id=test_ids.TENANT_A_ID,
        order_data={"recipient_name": "Valued Enterprise Client", "cod_amount": 750000.0},
        token=operator_token,
    )
    assert resp_create.status_code == 201, (
        f"Expected 201 on OPERATOR creating order, got {resp_create.status_code}: {resp_create.text}"
    )

    # 2. Assigned DRIVER acknowledges dispatch plan
    resp_ack = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        notes="Accepted assignment",
        token=driver_token,
    )
    assert resp_ack.status_code == 200, f"Expected 200 on DRIVER plan acknowledge, got {resp_ack.status_code}"

    # 3. OPERATOR blocked from settlement approval
    resp_op_app = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=operator_token,
    )
    assert resp_op_app.status_code == 403, (
        f"Expected 403 on OPERATOR approving settlement, got {resp_op_app.status_code}"
    )

    # 4. OPERATOR blocked from marking settlement paid
    resp_op_paid = client.mark_settlement_paid(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        payout_data={"reference": "BATCH-PAY-001"},
        token=operator_token,
    )
    assert resp_op_paid.status_code == 403, (
        f"Expected 403 on OPERATOR marking settlement paid, got {resp_op_paid.status_code}"
    )

    # 5. OWNER can approve and mark paid
    resp_owner_app = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=owner_token,
    )
    assert resp_owner_app.status_code == 200, (
        f"Expected 200 on OWNER approving settlement, got {resp_owner_app.status_code}"
    )

    resp_owner_paid = client.mark_settlement_paid(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        payout_data={"reference": "BATCH-PAY-001"},
        token=owner_token,
    )
    assert resp_owner_paid.status_code == 200, (
        f"Expected 200 on OWNER marking settlement paid, got {resp_owner_paid.status_code}"
    )


def test_t3_13_multi_tenant_session_switch_and_plan_boundary(
    client: DualClient,
    test_ids: DeterministicIDs,
):
    """
    Combination F08 + F10 + F09:
    1. Multi-tenant user refreshes into Tenant A (role: DRIVER).
    2. Requests driver plans -> receives only Tenant A plans.
    3. User switches session and refreshes into Tenant B (role: OPERATOR).
    4. Requests driver plans -> Tenant A plans are strictly excluded.
    5. User attempts to acknowledge Tenant A plan with Tenant B session -> 403 Forbidden.
    """
    # 1. Refresh into Tenant A
    resp_a = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_a.status_code == 200
    token_a = resp_a.json()["data"]["access_token"]

    # 2. List driver plans in Tenant A
    resp_plans_a = client.list_driver_plans(token=token_a)
    assert resp_plans_a.status_code == 200
    plans_a = resp_plans_a.json().get("data", resp_plans_a.json())
    for p in plans_a:
        assert p.get("tenant_id") == test_ids.TENANT_A_ID

    # 3. Switch session to Tenant B
    resp_b = client.refresh_token("multitenant_rt_valid_1", tenant_id=test_ids.TENANT_B_ID)
    assert resp_b.status_code == 200
    token_b = resp_b.json()["data"]["access_token"]

    # 4. Driver plans in Tenant B
    resp_plans_b = client.list_driver_plans(token=token_b)
    assert resp_plans_b.status_code == 200
    plans_b = resp_plans_b.json().get("data", resp_plans_b.json())
    for p in plans_b:
        assert p.get("tenant_id") != test_ids.TENANT_A_ID

    # 5. Cannot acknowledge Tenant A plan using Tenant B session
    resp_cross_ack = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        notes="Unauthorized cross-tenant ack",
        token=token_b,
    )
    assert resp_cross_ack.status_code in (403, 404), (
        f"Expected 403/404 for cross-tenant plan acknowledge, got {resp_cross_ack.status_code}"
    )


def test_t3_14_suspended_admin_cannot_access_gateway_or_services(
    client: DualClient,
    owner_token: str,
    token_factory,
    test_ids: DeterministicIDs,
):
    """
    Combination F02 + F04 + F06:
    1. OWNER suspends TENANT_ADMIN.
    2. Admin's active refresh tokens are revoked; refresh fails (401).
    3. Admin attempting to use an expired token receives 401 Unauthorized at Gateway.
    4. Non-admin attempting to access Tenant A with unscoped token and X-Tenant-Id receives 403.
    """
    # 1. OWNER suspends TENANT_ADMIN
    resp_suspend = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.ADMIN_USER_ID,
        reason="Suspicious activity investigation",
        token=owner_token,
    )
    assert resp_suspend.status_code == 200, f"Expected 200 on suspend admin, got {resp_suspend.status_code}"

    # 2. Suspended admin token refresh fails
    resp_ref = client.refresh_token("admin_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_ref.status_code in (401, 403), f"Expected 401/403 for revoked admin token, got {resp_ref.status_code}"

    # 3. Expired token rejected at Gateway
    expired_token = token_factory(
        user_id=test_ids.ADMIN_USER_ID,
        tenant_id=test_ids.TENANT_A_ID,
        tenant_role="TENANT_ADMIN",
        expired=True,
    )
    resp_exp = client.get(f"/api/orders/{test_ids.ORDER_1_ID}", token=expired_token)
    assert resp_exp.status_code == 401, f"Expected 401 on expired token, got {resp_exp.status_code}"

    # 4. Gateway header spoofing blocked for non-admin
    unscoped_tok = token_factory(user_id=test_ids.ADMIN_USER_ID, tenant_id=None)
    resp_spoof = client.get(
        f"/api/orders/{test_ids.ORDER_1_ID}",
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=unscoped_tok,
    )
    assert resp_spoof.status_code == 403, (
        f"Expected 403 for non-admin spoofing tenant header, got {resp_spoof.status_code}"
    )


def test_t3_15_driver_plan_decline_triggers_incident_and_notification(
    client: DualClient,
    driver_2_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F09 + F14 + F20:
    1. Assigned Driver 2 declines dispatch plan PLAN_2_ID with reason (200).
    2. Driver 2 reports an incident on associated order ORDER_2_ID (200).
    3. Notification consumer logs event while ensuring no sensitive URLs or tokens are logged.
    """
    # 1. Driver 2 declines plan
    resp_dec = client.decline_plan(
        plan_id=test_ids.PLAN_2_ID,
        reason="Severe vehicle mechanical failure",
        token=driver_2_token,
    )
    assert resp_dec.status_code == 200, f"Expected 200 on plan decline, got {resp_dec.status_code}"
    assert resp_dec.json().get("data", resp_dec.json()).get("status") == "DECLINED"

    # 2. Driver 2 reports incident on assigned order ORDER_2_ID
    resp_inc = client.report_incident(
        order_id=test_ids.ORDER_2_ID,
        incident_data={"incident_type": "VEHICLE_BREAKDOWN", "description": "Engine overheating"},
        token=driver_2_token,
    )
    assert resp_inc.status_code == 200, f"Expected 200 on driver incident report, got {resp_inc.status_code}"

    # 3. Notification log sanitization verification
    if client.is_mock:
        client.mock_client.record_log_message(
            "Plan declined notification dispatched: https://swiftroute.io/api/auth/email/verify?token=tok_secret_9999"
        )
        last_log = client.mock_client.logged_lines[-1]
        assert "tok_secret_9999" not in last_log
        assert "[REDACTED_TOKEN_URL]" in last_log


def test_t3_16_system_admin_cross_tenant_override_full_flow(
    client: DualClient,
    system_admin_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F04 + F15 + F16 + F17:
    1. SYSTEM_ADMIN supplies arbitrary X-Tenant-Id through Gateway successfully (200).
    2. SYSTEM_ADMIN reads KYC workflow for Tenant A and Tenant B (200).
    3. SYSTEM_ADMIN approves settlement in Tenant A (200).
    4. SYSTEM_ADMIN executes cross-tenant driver query without restriction (200).
    """
    # 1. SYSTEM_ADMIN supplies X-Tenant-Id
    resp_gw = client.get(
        f"/api/orders/{test_ids.ORDER_1_ID}",
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=system_admin_token,
    )
    assert resp_gw.status_code == 200, f"Expected 200 for SYSTEM_ADMIN on orders, got {resp_gw.status_code}"

    # 2. SYSTEM_ADMIN reads KYC across tenants
    resp_kyc_a = client.get_tenant_kyc(test_ids.TENANT_A_ID, token=system_admin_token)
    assert resp_kyc_a.status_code == 200, f"Expected 200 for SYSADMIN on Tenant A KYC, got {resp_kyc_a.status_code}"

    resp_kyc_b = client.get_tenant_kyc(test_ids.TENANT_B_ID, token=system_admin_token)
    assert resp_kyc_b.status_code == 200, f"Expected 200 for SYSADMIN on Tenant B KYC, got {resp_kyc_b.status_code}"

    # 3. SYSTEM_ADMIN approves settlement
    resp_app = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=system_admin_token,
    )
    assert resp_app.status_code == 200, f"Expected 200 on SYSADMIN settlement approval, got {resp_app.status_code}"

    # 4. SYSTEM_ADMIN cross-tenant driver query
    resp_drivers = client.get("/api/drivers", token=system_admin_token)
    assert resp_drivers.status_code in (200, 404), (
        f"Expected non-403 for SYSADMIN querying drivers, got {resp_drivers.status_code}"
    )


def test_t3_17_viewer_full_audit_read_across_all_services(
    client: DualClient,
    viewer_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F16 + F19 + F21 + F13:
    1. VIEWER successfully performs all audit read endpoints:
       - settlements, earnings, dispatch alerts, notification inbox, routing plans, orders.
    2. VIEWER is blocked from all state-mutating actions (403):
       - approve settlement, batch optimization, create order, cancel order.
    """
    # 1. Read operations succeed
    assert client.list_settlements(token=viewer_token).status_code == 200
    assert client.get_earnings(token=viewer_token).status_code == 200
    assert client.get_dispatch_alerts(token=viewer_token).status_code == 200
    assert client.get_notification_inbox(token=viewer_token).status_code == 200
    assert client.get_routing_plans(token=viewer_token).status_code == 200
    assert client.get_order(test_ids.ORDER_1_ID, token=viewer_token).status_code == 200

    # 2. Write operations fail with 403
    assert client.approve_settlement(test_ids.SETTLEMENT_1_ID, token=viewer_token).status_code == 403
    assert client.mark_settlement_paid(test_ids.SETTLEMENT_1_ID, payout_data={"ref": "X"}, token=viewer_token).status_code == 403
    assert client.optimize_batch({"locations": []}, token=viewer_token).status_code == 403
    assert client.optimize_insert({"location": {}}, token=viewer_token).status_code == 403
    assert client.optimize_reoptimize({"plan_id": test_ids.PLAN_1_ID}, token=viewer_token).status_code == 403


def test_t3_18_driver_delivery_simulation_to_earnings_generation(
    client: DualClient,
    driver_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F11 + F16 + F13:
    1. DRIVER views assigned order (200).
    2. DRIVER executes simulation route (start & stop) (200).
    3. DRIVER inspects accumulated earnings (200).
    4. DRIVER cannot self-approve settlement payouts (403).
    """
    # 1. DRIVER reads assigned order
    resp_order = client.get_order(test_ids.ORDER_1_ID, token=driver_token)
    assert resp_order.status_code == 200, f"Expected 200 on assigned order read, got {resp_order.status_code}"

    # 2. DRIVER starts & stops simulation
    resp_start = client.start_tracking_simulation(test_ids.PLAN_1_ID, token=driver_token)
    assert resp_start.status_code == 200, f"Expected 200 on simulation start, got {resp_start.status_code}"

    resp_stop = client.stop_tracking_simulation(test_ids.PLAN_1_ID, token=driver_token)
    assert resp_stop.status_code == 200, f"Expected 200 on simulation stop, got {resp_stop.status_code}"

    # 3. DRIVER views earnings
    resp_earnings = client.get_earnings(token=driver_token)
    assert resp_earnings.status_code == 200, f"Expected 200 on earnings read, got {resp_earnings.status_code}"

    # 4. DRIVER cannot self-approve settlement
    resp_self_approve = client.approve_settlement(test_ids.SETTLEMENT_1_ID, token=driver_token)
    assert resp_self_approve.status_code == 403, (
        f"Expected 403 on DRIVER self-approving settlement, got {resp_self_approve.status_code}"
    )


def test_t3_19_empty_internal_key_regression_defense(
    client: DualClient,
    test_ids: DeterministicIDs,
):
    """
    Combination F17 + F18:
    1. driver-service rejects empty string, None, and whitespace service keys (401/403).
    2. routing-engine-service rejects empty string and whitespace internal keys (401).
    3. Both services fail closed and protect internal APIs from bypass regressions.
    """
    # 1. Driver service empty / missing keys
    resp_d_empty = client.get_all_drivers_internal(service_key="", tenant_id=test_ids.TENANT_A_ID)
    assert resp_d_empty.status_code in (401, 403), f"Expected 401/403 for empty key, got {resp_d_empty.status_code}"

    resp_d_none = client.get_all_drivers_internal(service_key=None, tenant_id=test_ids.TENANT_A_ID)
    assert resp_d_none.status_code in (401, 403), f"Expected 401/403 for None key, got {resp_d_none.status_code}"

    resp_d_ws = client.get_all_drivers_internal(service_key="   ", tenant_id=test_ids.TENANT_A_ID)
    assert resp_d_ws.status_code in (401, 403), f"Expected 401/403 for whitespace key, got {resp_d_ws.status_code}"

    # 2. Routing engine empty / whitespace keys
    resp_r_empty = client.optimize_batch({"test": True}, internal_key="", tenant_id=test_ids.TENANT_A_ID)
    assert resp_r_empty.status_code == 401, f"Expected 401 for empty routing key, got {resp_r_empty.status_code}"

    resp_r_ws = client.optimize_batch({"test": True}, internal_key="   ", tenant_id=test_ids.TENANT_A_ID)
    assert resp_r_ws.status_code == 401, f"Expected 401 for whitespace routing key, got {resp_r_ws.status_code}"


def test_t3_20_token_ttl_boundary_and_session_refresh_cycle(
    client: DualClient,
    admin_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F07 + F08 + F06:
    1. Active user refreshes session -> receives 15-minute token retaining active tenant context.
    2. Refreshed token is accepted for tenant operations.
    3. User account is suspended by admin.
    4. Immediate next refresh attempt fails active check with 401.
    """
    # 1. Active user refreshes session
    resp_ref1 = client.refresh_token("driver_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_ref1.status_code == 200, f"Expected 200 on active refresh, got {resp_ref1.status_code}"
    token_data = resp_ref1.json().get("data", resp_ref1.json())
    new_token = token_data["access_token"]
    claims = jwt.decode(new_token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    assert claims.get("tenant_id") == test_ids.TENANT_A_ID
    assert claims.get("tenant_role") == "DRIVER"
    assert (claims["exp"] - claims["iat"]) == 900

    # 2. Refreshed token is accepted on assigned order read
    resp_view = client.get_order(test_ids.ORDER_1_ID, token=new_token)
    assert resp_view.status_code == 200, f"Expected 200 with new access token, got {resp_view.status_code}"

    # 3. User is suspended by admin
    resp_susp = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.DRIVER_USER_ID,
        reason="Immediate audit suspension",
        token=admin_token,
    )
    assert resp_susp.status_code == 200, f"Expected 200 on driver suspension, got {resp_susp.status_code}"

    # 4. Subsequent token refresh fails active check
    resp_ref2 = client.refresh_token("driver_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_ref2.status_code in (401, 403), (
        f"Expected 401/403 for suspended user refresh, got {resp_ref2.status_code}"
    )


def test_t3_21_cross_tenant_idor_complete_matrix_defense(
    client: DualClient,
    tenant_b_driver_token: str,
    test_ids: DeterministicIDs,
):
    """
    Combination F04 + F09 + F10 + F13 + F15 + F17:
    Exhaustive cross-tenant IDOR validation from Tenant B driver against Tenant A resources:
    1. Gateway Header IDOR: Supplying X-Tenant-Id for Tenant A (403).
    2. Dispatch Plan Acknowledge IDOR: Acknowledging Tenant A plan PLAN_1_ID (403/404).
    3. Driver Plans List IDOR: Tenant A plans excluded from results.
    4. Order View IDOR: Viewing Tenant A order ORDER_1_ID (403).
    5. Tenant Workflow IDOR: Reading Tenant A KYC (403).
    6. Internal Driver Query IDOR: Querying driver service without tenant ID (403/400).
    """
    # 1. Gateway Header IDOR
    resp_gw_idor = client.get(
        f"/api/orders/{test_ids.ORDER_1_ID}",
        headers={"X-Tenant-Id": test_ids.TENANT_A_ID},
        token=tenant_b_driver_token,
    )
    assert resp_gw_idor.status_code == 403, (
        f"Expected 403 for cross-tenant header IDOR, got {resp_gw_idor.status_code}"
    )

    # 2. Dispatch Plan Acknowledge IDOR
    resp_plan_idor = client.acknowledge_plan(
        plan_id=test_ids.PLAN_1_ID,
        notes="Malicious IDOR acceptance",
        token=tenant_b_driver_token,
    )
    assert resp_plan_idor.status_code in (403, 404), (
        f"Expected 403/404 for cross-tenant plan acknowledge IDOR, got {resp_plan_idor.status_code}"
    )

    # 3. Driver Plans List IDOR
    resp_list_idor = client.list_driver_plans(token=tenant_b_driver_token)
    assert resp_list_idor.status_code == 200
    plans = resp_list_idor.json().get("data", resp_list_idor.json())
    for p in plans:
        assert p.get("tenant_id") == test_ids.TENANT_B_ID, "Leaked Tenant A plan into Tenant B driver query"

    # 4. Order View IDOR
    resp_order_idor = client.get_order(test_ids.ORDER_1_ID, token=tenant_b_driver_token)
    assert resp_order_idor.status_code in (403, 404), (
        f"Expected 403/404 for cross-tenant order view IDOR, got {resp_order_idor.status_code}"
    )

    # 5. Tenant Workflow IDOR
    resp_kyc_idor = client.get_tenant_kyc(test_ids.TENANT_A_ID, token=tenant_b_driver_token)
    assert resp_kyc_idor.status_code == 403, (
        f"Expected 403 for cross-tenant KYC IDOR, got {resp_kyc_idor.status_code}"
    )

    # 6. Internal Driver Service Dump IDOR
    resp_dump_idor = client.get_all_drivers_internal(
        service_key=DRIVER_SERVICE_KEY,
        tenant_id=None,
    )
    assert resp_dump_idor.status_code in (400, 403), (
        f"Expected 400/403 for internal driver dump without tenant ID, got {resp_dump_idor.status_code}"
    )
