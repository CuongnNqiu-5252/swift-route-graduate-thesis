-- ==============================================================================
-- auth_db: V3__create_tenant_invitations_schema.sql
-- Table for tenant member invitations with secure hashed tokens
-- ==============================================================================

CREATE TABLE IF NOT EXISTS tenant_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'OPERATOR'
        CHECK (role IN ('OWNER', 'ADMIN', 'OPERATOR', 'DRIVER', 'VIEWER')),
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    invited_by UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_invitations_tenant ON tenant_invitations(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_invitations_email ON tenant_invitations(email);
CREATE INDEX IF NOT EXISTS idx_tenant_invitations_token_hash ON tenant_invitations(token_hash);
CREATE INDEX IF NOT EXISTS idx_tenant_invitations_status ON tenant_invitations(status);

