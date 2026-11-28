-- V11__tenant_granular_permissions_and_session_scope.sql
-- Add per-member granular permission overrides, enterprise role templates, and session tenant scope

ALTER TABLE tenant_memberships
    ADD COLUMN IF NOT EXISTS granted_permissions TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS revoked_permissions TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS permissions_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE tenant_invitations
    ADD COLUMN IF NOT EXISTS granted_permissions TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS revoked_permissions TEXT NOT NULL DEFAULT '[]';

CREATE TABLE IF NOT EXISTS tenant_role_permissions (
    tenant_id UUID NOT NULL,
    membership_role VARCHAR(30) NOT NULL,
    permissions TEXT NOT NULL DEFAULT '[]',
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, membership_role),
    CONSTRAINT tenant_role_permissions_role_check
        CHECK (membership_role IN ('TENANT_ADMIN', 'OPERATOR', 'DRIVER', 'VIEWER'))
);

ALTER TABLE refresh_token
    ADD COLUMN IF NOT EXISTS active_tenant_id UUID;

CREATE INDEX IF NOT EXISTS idx_refresh_token_active_tenant
    ON refresh_token (user_id, active_tenant_id) WHERE revoked = false;
