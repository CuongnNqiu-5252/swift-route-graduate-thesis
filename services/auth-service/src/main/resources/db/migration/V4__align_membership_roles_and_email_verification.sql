-- ==============================================================================
-- auth_db: V4__align_membership_roles_and_email_verification.sql
-- Align membership roles, extend global roles, and add email verification schema
-- ==============================================================================

-- 1. Align tenant_memberships role check constraint with TenantMembershipRole enum and V3 invitations
ALTER TABLE tenant_memberships DROP CONSTRAINT IF EXISTS tenant_memberships_membership_role_check;
ALTER TABLE tenant_memberships ADD CONSTRAINT tenant_memberships_membership_role_check
    CHECK (membership_role IN ('OWNER', 'ADMIN', 'OPERATOR', 'DRIVER', 'VIEWER', 'STAFF', 'MANAGER'));

-- Ensure unique membership per tenant & user
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_tenant_user_membership'
    ) THEN
        ALTER TABLE tenant_memberships ADD CONSTRAINT uq_tenant_user_membership UNIQUE (tenant_id, user_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_memberships_user ON tenant_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_memberships_tenant ON tenant_memberships(tenant_id);

-- 2. Align users role check constraint with Role enum
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('SYSTEM_ADMIN', 'OPS_STAFF', 'DRIVER', 'CUSTOMER', 'ADMIN', 'OWNER', 'TENANT_ADMIN', 'PARTNER', 'TENANT_OWNER'));

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users(tenant_id);

-- 3. Add email verification fields to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;

-- 4. Create table for email verification tokens with secure SHA-256 hashes
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_email_verification_token_hash ON email_verification_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_email_verification_user ON email_verification_tokens(user_id);
