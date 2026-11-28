-- V13__driver_app_activation.sql
-- Driver App Activation keys (HMAC-SHA256 hashed, single-use, 24h TTL) and session_id tracking for device revocation

CREATE TABLE IF NOT EXISTS driver_activation_keys (
    activation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_driver_activation_keys_tenant_driver
    ON driver_activation_keys(tenant_id, driver_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_driver_activation_keys_active
    ON driver_activation_keys(tenant_id, driver_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

ALTER TABLE refresh_token
    ADD COLUMN IF NOT EXISTS session_id UUID;

CREATE INDEX IF NOT EXISTS idx_refresh_token_session_id
    ON refresh_token(session_id)
    WHERE session_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS revoked_auth_sessions (
    session_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    driver_id UUID,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_revoked_auth_sessions_expires_at
    ON revoked_auth_sessions(expires_at);
