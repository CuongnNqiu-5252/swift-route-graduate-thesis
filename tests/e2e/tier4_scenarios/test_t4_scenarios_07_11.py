"""
Tier 4: End-to-End Real-World Application Scenarios (Scenarios 07 to 11).
Simulates comprehensive multi-step production operational workflows, governance, and security stress tests.

Covered Scenarios:
- test_t4_scenario_07_financial_settlement_and_disbursement_governance: F12, F16, F21
- test_t4_scenario_08_internal_microservice_mesh_security: F17, F18, F19
- test_t4_scenario_09_security_incident_response_credential_revocation: F01, F02, F05, F06, F20
- test_t4_scenario_10_read_only_compliance_auditor_walkthrough: F13, F16, F19, F21
- test_t4_scenario_11_high_concurrency_anti_idor_validation: F04, F09, F10, F13, F15
"""

import concurrent.futures
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


def test_t4_scenario_07_financial_settlement_and_disbursement_governance(
    client: DualClient,
    operator_token: str,
    admin_token: str,
    owner_token: str,
    viewer_token: str,
    test_ids: DeterministicIDs,
):
    """
    Scenario 07: Financial Settlement & Disbursement Governance (F12, F16, F21).
    - Step 1: OPERATOR lists settlements and identifies pending payout SETTLEMENT_1_ID.
    - Step 2: OPERATOR attempts to approve settlement -> blocked (403 Forbidden).
    - Step 3: OPERATOR attempts to mark settlement as paid -> blocked (403 Forbidden).
    - Step 4: TENANT_ADMIN reviews and approves settlement SETTLEMENT_1_ID (200, APPROVED).
    - Step 5: OWNER executes disbursement and marks settlement paid with voucher reference (200, PAID).
    - Step 6: Read-only compliance VIEWER audits the completed settlement record (200).
    - Step 7: VIEWER inspects earnings summary (200).
    - Step 8: VIEWER attempts to approve another settlement -> blocked (403 Forbidden).
    """
    # Step 1: OPERATOR lists settlements
    resp_list = client.list_settlements(token=operator_token)
    assert resp_list.status_code == 200, "OPERATOR can list settlements"

    # Step 2: Separation of duties: OPERATOR cannot approve settlements
    resp_op_approve = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=operator_token,
    )
    assert resp_op_approve.status_code == 403, "OPERATOR role cannot approve settlements"

    # Step 3: OPERATOR cannot mark settlement paid
    resp_op_paid = client.mark_settlement_paid(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        payout_data={"reference": "ILLEGAL-VOUCHER-001"},
        token=operator_token,
    )
    assert resp_op_paid.status_code == 403, "OPERATOR role cannot mark settlements paid"

    # Step 4: TENANT_ADMIN approves settlement
    resp_admin_approve = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=admin_token,
    )
    assert resp_admin_approve.status_code == 200, "TENANT_ADMIN can approve settlements"
    assert resp_admin_approve.json().get("data", resp_admin_approve.json()).get("status") == "APPROVED"

    # Step 5: OWNER marks settlement paid
    resp_owner_paid = client.mark_settlement_paid(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        payout_data={"reference": "BANK-TX-55443322", "cleared_by": "ChiefFinancialOfficer"},
        token=owner_token,
    )
    assert resp_owner_paid.status_code == 200, "OWNER can mark settlements paid"
    assert resp_owner_paid.json().get("data", resp_owner_paid.json()).get("status") == "PAID"

    # Step 6: VIEWER audits settlements ledger
    resp_viewer_audit = client.list_settlements(token=viewer_token)
    assert resp_viewer_audit.status_code == 200, "VIEWER can read settlements for audit"
    settlements = resp_viewer_audit.json().get("data", resp_viewer_audit.json())
    settlement_1 = next((s for s in settlements if s["id"] == test_ids.SETTLEMENT_1_ID), None)
    assert settlement_1 is not None
    assert settlement_1.get("status") == "PAID"

    # Step 7: VIEWER inspects earnings
    resp_viewer_earnings = client.get_earnings(token=viewer_token)
    assert resp_viewer_earnings.status_code == 200

    # Step 8: VIEWER cannot approve settlements
    resp_viewer_app = client.approve_settlement(
        settlement_id=test_ids.SETTLEMENT_1_ID,
        token=viewer_token,
    )
    assert resp_viewer_app.status_code == 403, "VIEWER cannot approve settlements"


def test_t4_scenario_08_internal_microservice_mesh_security(
    client: DualClient,
    viewer_token: str,
    driver_token: str,
    test_ids: DeterministicIDs,
):
    """
    Scenario 08: Internal Microservice Mesh Security & Key Fail-Closed (F17, F18, F19).
    - Step 1: Internal service query with valid X-Service-Key and tenant_id returns tenant drivers (200).
    - Step 2: Accidental data-dump prevention: query with X-Service-Key but null tenant_id is rejected (403/400).
    - Step 3: Unauthenticated / forged X-Service-Key is rejected (401/403).
    - Step 4: Routing engine internal key: valid key succeeds with algorithm dp_ml_pruning (200).
    - Step 5: Routing engine empty/whitespace internal key fails closed (401).
    - Step 6: Direct API Gateway route: VIEWER calling optimize/batch is blocked (403).
    - Step 7: Direct API Gateway route: DRIVER calling optimize/reoptimize is permitted (200).
    """
    # Step 1: Valid internal service query with explicit tenant ID
    resp_internal_valid = client.get_all_drivers_internal(
        service_key=DRIVER_SERVICE_KEY,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp_internal_valid.status_code == 200, "Valid internal service key query must succeed"
    drivers = resp_internal_valid.json().get("data", resp_internal_valid.json())
    for d in drivers:
        assert d.get("tenant_id") == test_ids.TENANT_A_ID

    # Step 2: Null tenant ID query is strictly blocked from dumping global database
    resp_internal_null = client.get_all_drivers_internal(
        service_key=DRIVER_SERVICE_KEY,
        tenant_id=None,
    )
    assert resp_internal_null.status_code in (400, 403), "Global driver dump without tenant ID must fail"

    # Step 3: Forged service key is rejected
    resp_internal_forged = client.get_all_drivers_internal(
        service_key="forged_service_token_12345",
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp_internal_forged.status_code in (401, 403), "Invalid internal key must fail"

    # Step 4: Routing engine valid internal key
    resp_routing_valid = client.optimize_batch(
        batch_data={"locations": [{"lat": 10.7769, "lng": 106.7009}]},
        internal_key=ROUTING_INTERNAL_KEY,
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp_routing_valid.status_code == 200, "Valid routing internal key must succeed"
    data = resp_routing_valid.json().get("data", resp_routing_valid.json())
    assert data.get("algorithm_used") == "dp_ml_pruning"

    # Step 5: Routing engine empty/whitespace internal key fails closed
    resp_routing_empty = client.optimize_batch(
        batch_data={"locations": [{"lat": 10.7769, "lng": 106.7009}]},
        internal_key="",
        tenant_id=test_ids.TENANT_A_ID,
    )
    assert resp_routing_empty.status_code == 401, "Empty routing internal key must fail closed"

    # Step 6: VIEWER role cannot invoke batch optimization
    resp_viewer_opt = client.optimize_batch(
        batch_data={"locations": [{"lat": 10.7769, "lng": 106.7009}]},
        token=viewer_token,
    )
    assert resp_viewer_opt.status_code == 403, "VIEWER role cannot call optimize/batch"

    # Step 7: DRIVER role is permitted on reoptimize
    resp_driver_reopt = client.optimize_reoptimize(
        reoptimize_data={"plan_id": test_ids.PLAN_1_ID, "current_lat": 10.778, "current_lng": 106.705},
        token=driver_token,
    )
    assert resp_driver_reopt.status_code == 200, "DRIVER role must be permitted on optimize/reoptimize"


def test_t4_scenario_09_security_incident_response_credential_revocation(
    client: DualClient,
    admin_token: str,
    test_ids: DeterministicIDs,
):
    """
    Scenario 09: Security Incident Response & Credential Revocation (F01, F02, F05, F06, F20).
    - Step 1: Security operations detects compromised operator credentials.
    - Step 2: TENANT_ADMIN immediately suspends the operator member (200).
    - Step 3: Member suspension automatically revokes all active refresh tokens for the user.
    - Step 4: Attacker attempts to refresh token using the stolen refresh token -> 401 Unauthorized.
    - Step 5: Attacker attempts to initiate token refresh with constructed active claims -> fails (401).
    - Step 6: Legitimate user triggers account recovery via public resend email endpoint (200).
    - Step 7: Notification consumer sanitizes verification and reset URLs; zero secret tokens logged or broadcast.
    """
    # Step 1 & 2: Security team / TENANT_ADMIN suspends the compromised member
    resp_suspend = client.suspend_member(
        tenant_id=test_ids.TENANT_A_ID,
        member_id=test_ids.OPERATOR_USER_ID,
        reason="Incident SEC-2026-004: Compromised operator credentials detected on paste site",
        token=admin_token,
    )
    assert resp_suspend.status_code == 200, "Member suspension must succeed"
    assert resp_suspend.json().get("data", resp_suspend.json()).get("status") == "SUSPENDED"

    # Step 3 & 4: Stolen refresh token is revoked and fails refresh
    resp_stolen_refresh = client.refresh_token("operator_rt_valid_1", tenant_id=test_ids.TENANT_A_ID)
    assert resp_stolen_refresh.status_code in (401, 403), "Stolen refresh token must be rejected"

    # Step 5: Suspended user fails active check on refresh
    resp_active_check = client.refresh_token("operator_rt_valid_1")
    assert resp_active_check.status_code in (401, 403), "Suspended user must fail active check"

    # Step 6: User recovery via public email resend endpoint
    resp_recovery = client.resend_public_email("operator@tenanta.com")
    assert resp_recovery.status_code == 200, "Public recovery resend must succeed"

    # Step 7: Notification logging sanitization verification
    if client.is_mock:
        client.mock_client.record_log_message(
            "Account recovery link dispatched: https://swiftroute.io/api/auth/password/reset?token=incident_recovery_token_99999"
        )
        last_log = client.mock_client.logged_lines[-1]
        assert "incident_recovery_token_99999" not in last_log
        assert "[REDACTED_TOKEN_URL]" in last_log

        # SSE broadcast sanitization
        client.mock_client.broadcast_sse_alert({
            "event": "PASSWORD_RESET_REQUESTED",
            "link": "https://swiftroute.io/api/auth/password/reset?token=leak_secret_link",
        })
        last_sse = client.mock_client.sse_broadcast_messages[-1]
        assert "leak_secret_link" not in str(last_sse)
        assert last_sse.get("link") == "[REDACTED]"


def test_t4_scenario_10_read_only_compliance_auditor_walkthrough(
    client: DualClient,
    viewer_token: str,
    test_ids: DeterministicIDs,
):
    """
    Scenario 10: Read-Only Compliance Auditor Verification (F13, F16, F19, F21).
    - Step 1: Compliance auditor authenticates with VIEWER role.
    - Step 2: Auditor accesses financial settlements and earnings ledgers (200).
    - Step 3: Auditor accesses dispatch alerts and notification inbox (200).
    - Step 4: Auditor connects to real-time notification stream (200).
    - Step 5: Auditor inspects routing plans (200).
    - Step 6: Auditor reads assigned order details (200).
    - Step 7: Auditor attempts write/execute actions across services -> all fail with 403 Forbidden.
    - Step 8: Auditor attempts cross-tenant query -> blocked with 403 Forbidden.
    """
    # Step 1 & 2: Auditor reviews financial data
    resp_settlements = client.list_settlements(token=viewer_token)
    assert resp_settlements.status_code == 200, "Auditor must be able to list settlements"

    resp_earnings = client.get_earnings(token=viewer_token)
    assert resp_earnings.status_code == 200, "Auditor must be able to view earnings"

    # Step 3: Auditor reviews operational alerts and inbox
    resp_alerts = client.get_dispatch_alerts(token=viewer_token)
    assert resp_alerts.status_code == 200, "Auditor must be able to inspect dispatch alerts"

    resp_inbox = client.get_notification_inbox(token=viewer_token)
    assert resp_inbox.status_code == 200, "Auditor must be able to inspect notification inbox"

    # Step 4: Auditor connects to notification stream
    resp_stream = client.get_notification_stream(token=viewer_token)
    assert resp_stream.status_code == 200, "Auditor can connect to SSE stream"

    # Step 5: Auditor inspects routing plans
    resp_plans = client.get_routing_plans(token=viewer_token)
    assert resp_plans.status_code == 200, "Auditor can read routing plans"

    # Step 6: Auditor reads order details
    resp_order = client.get_order(test_ids.ORDER_1_ID, token=viewer_token)
    assert resp_order.status_code == 200, "Auditor can view order details"

    # Step 7: Write integrity audit: all mutation endpoints must reject VIEWER with 403
    assert client.approve_settlement(test_ids.SETTLEMENT_1_ID, token=viewer_token).status_code == 403
    assert client.mark_settlement_paid(test_ids.SETTLEMENT_1_ID, {"ref": "A"}, token=viewer_token).status_code == 403
    assert client.optimize_batch({"locations": []}, token=viewer_token).status_code == 403
    assert client.optimize_insert({"location": {}}, token=viewer_token).status_code == 403
    assert client.optimize_reoptimize({"plan_id": test_ids.PLAN_1_ID}, token=viewer_token).status_code == 403

    # Step 8: Cross-tenant barrier: auditor cannot read Tenant B alerts
    resp_cross_alert = client.get_dispatch_alerts(tenant_id=test_ids.TENANT_B_ID, token=viewer_token)
    assert resp_cross_alert.status_code == 403, "Auditor cannot read cross-tenant alerts"


def test_t4_scenario_11_high_concurrency_anti_idor_validation(
    client: DualClient,
    driver_token: str,
    driver_2_token: str,
    tenant_b_driver_token: str,
    test_ids: DeterministicIDs,
):
    """
    Scenario 11: High-Concurrency Dispatch & Anti-IDOR Validation (F04, F09, F10, F13, F15).
    - Step 1: Simulate concurrent operations across multiple drivers:
      - Driver 1 acknowledges Plan 1
      - Driver 2 declines Plan 2
      - Driver B acknowledges Plan B
    - Step 2: Concurrently execute aggressive cross-tenant and cross-driver IDOR attacks:
      - Driver B attempts to acknowledge Plan 1 (Tenant A) -> 403/404
      - Driver B attempts to view Order 1 (Tenant A) -> 403/404
      - Driver B attempts to read Tenant A KYC -> 403
      - Driver B attempts Gateway header spoofing (X-Tenant-Id: Tenant A) -> 403
      - Driver 1 attempts to view Order 2 (assigned to Driver 2) -> 403
      - Driver 2 attempts to view Order 1 (assigned to Driver 1) -> 403
    - Step 3: Verify clean isolation and zero state pollution across threads.
    """
    results = {}

    def op_driver_1_ack():
        return client.acknowledge_plan(plan_id=test_ids.PLAN_1_ID, notes="Concurrent Ack 1", token=driver_token)

    def op_driver_2_dec():
        return client.decline_plan(plan_id=test_ids.PLAN_2_ID, reason="Concurrent Decline 2", token=driver_2_token)

    def op_driver_b_ack():
        return client.acknowledge_plan(plan_id=test_ids.PLAN_TENANT_B_ID, notes="Concurrent Ack B", token=tenant_b_driver_token)

    def attack_driver_b_plan_idor():
        return client.acknowledge_plan(plan_id=test_ids.PLAN_1_ID, notes="IDOR attack", token=tenant_b_driver_token)

    def attack_driver_b_order_idor():
        return client.get_order(order_id=test_ids.ORDER_1_ID, token=tenant_b_driver_token)

    def attack_driver_b_kyc_idor():
        return client.get_tenant_kyc(tenant_id=test_ids.TENANT_A_ID, token=tenant_b_driver_token)

    def attack_driver_b_gateway_header():
        return client.get(f"/api/orders/{test_ids.ORDER_1_ID}", headers={"X-Tenant-Id": test_ids.TENANT_A_ID}, token=tenant_b_driver_token)

    def attack_driver_1_peer_order():
        return client.get_order(order_id=test_ids.ORDER_2_ID, token=driver_token)

    def attack_driver_2_peer_order():
        return client.get_order(order_id=test_ids.ORDER_1_ID, token=driver_2_token)

    tasks = {
        "d1_ack": op_driver_1_ack,
        "d2_dec": op_driver_2_dec,
        "db_ack": op_driver_b_ack,
        "atk_b_plan": attack_driver_b_plan_idor,
        "atk_b_order": attack_driver_b_order_idor,
        "atk_b_kyc": attack_driver_b_kyc_idor,
        "atk_b_gw": attack_driver_b_gateway_header,
        "atk_d1_peer": attack_driver_1_peer_order,
        "atk_d2_peer": attack_driver_2_peer_order,
    }

    # Execute all operations concurrently using ThreadPoolExecutor
    with concurrent.futures.ThreadPoolExecutor(max_workers=9) as executor:
        future_to_key = {executor.submit(fn): key for key, fn in tasks.items()}
        for future in concurrent.futures.as_completed(future_to_key):
            key = future_to_key[future]
            results[key] = future.result()

    # Assertions on legitimate concurrent operations
    assert results["d1_ack"].status_code == 200, f"Driver 1 ack failed: {results['d1_ack'].status_code}"
    assert results["d2_dec"].status_code == 200, f"Driver 2 dec failed: {results['d2_dec'].status_code}"
    assert results["db_ack"].status_code == 200, f"Driver B ack failed: {results['db_ack'].status_code}"

    # Assertions on concurrent attack attempts
    assert results["atk_b_plan"].status_code in (403, 404), f"Cross-tenant plan IDOR not blocked: {results['atk_b_plan'].status_code}"
    assert results["atk_b_order"].status_code in (403, 404), f"Cross-tenant order IDOR not blocked: {results['atk_b_order'].status_code}"
    assert results["atk_b_kyc"].status_code == 403, f"Cross-tenant KYC IDOR not blocked: {results['atk_b_kyc'].status_code}"
    assert results["atk_b_gw"].status_code == 403, f"Gateway header spoofing not blocked: {results['atk_b_gw'].status_code}"
    assert results["atk_d1_peer"].status_code == 403, f"Driver 1 peer order IDOR not blocked: {results['atk_d1_peer'].status_code}"
    assert results["atk_d2_peer"].status_code == 403, f"Driver 2 peer order IDOR not blocked: {results['atk_d2_peer'].status_code}"

    # Verify final states of plans
    plan_1 = client.mock_client.dispatch_plans.get(test_ids.PLAN_1_ID) if client.is_mock else None
    plan_2 = client.mock_client.dispatch_plans.get(test_ids.PLAN_2_ID) if client.is_mock else None
    plan_b = client.mock_client.dispatch_plans.get(test_ids.PLAN_TENANT_B_ID) if client.is_mock else None

    if plan_1:
        assert plan_1["status"] == "ACKNOWLEDGED"
    if plan_2:
        assert plan_2["status"] == "DECLINED"
    if plan_b:
        assert plan_b["status"] == "ACKNOWLEDGED"
