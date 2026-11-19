"""
F15 (R3-TenantWorkflowProtection): Tenant Workflow & KYC Isolation in TenantWorkflowController.
Verifies that users without a tenant or from a different tenant cannot read KYC or onboarding
information of arbitrary tenants, while SYSTEM_ADMIN and the tenant creator can poll provisioning.
"""

import pytest
from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import DeterministicIDs


def test_f15_user_without_tenant_cannot_read_tenant_kyc(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """A user whose JWT lacks a tenant claim cannot read KYC data of an arbitrary tenant (403)."""
    resp = client.get_tenant_kyc(tenant_id=test_ids.TENANT_A_ID, token=unscoped_token)
    assert resp.status_code == 403, f"Expected 403 Forbidden for unscoped user KYC read, got {resp.status_code}: {resp.text}"


def test_f15_user_without_tenant_cannot_read_tenant_details(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """A user without a tenant claim cannot read onboarding details of an existing tenant (403)."""
    resp = client.get_tenant_workflow(tenant_id=test_ids.TENANT_A_ID, token=unscoped_token)
    assert resp.status_code == 403, f"Expected 403 Forbidden for unscoped user workflow read, got {resp.status_code}: {resp.text}"


def test_f15_cross_tenant_user_cannot_read_other_tenant_workflow(
    client: DualClient, admin_token: str, test_ids: DeterministicIDs
):
    """A tenant admin of Tenant A cannot read KYC or onboarding details of Tenant B (403)."""
    resp = client.get_tenant_kyc(tenant_id=test_ids.TENANT_B_ID, token=admin_token)
    assert resp.status_code == 403, f"Expected 403 Forbidden for cross-tenant KYC read, got {resp.status_code}: {resp.text}"


def test_f15_system_admin_can_read_tenant_workflow(
    client: DualClient, system_admin_token: str, test_ids: DeterministicIDs
):
    """SYSTEM_ADMIN can inspect workflow and onboarding details for compliance verification."""
    resp = client.get_tenant_workflow(tenant_id=test_ids.TENANT_A_ID, token=system_admin_token)
    assert resp.status_code == 200, f"Expected 200 OK for SYSTEM_ADMIN, got {resp.status_code}: {resp.text}"


def test_f15_unscoped_creator_can_poll_provisioning(
    client: DualClient, unscoped_token: str, test_ids: DeterministicIDs
):
    """An unscoped user who created a tenant can poll provisioning status during onboarding."""
    resp = client.poll_provisioning(tenant_id=test_ids.TENANT_PENDING_ID, token=unscoped_token)
    assert resp.status_code == 200, f"Expected 200 OK for creator polling provisioning, got {resp.status_code}: {resp.text}"
