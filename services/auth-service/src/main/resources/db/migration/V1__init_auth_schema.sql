-- ==============================================================================
-- auth_db: V1__init_auth_schema.sql
-- Baseline schema for Auth Service
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Bảng users
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER'
        CHECK (role IN ('ADMIN', 'OPS_STAFF', 'TENANT_OWNER', 'DRIVER', 'CUSTOMER')),
    tenant_id UUID, -- Logical reference to order_db.tenants (active_tenant_id)
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users(tenant_id);

-- 2. Bảng refresh_token
CREATE TABLE IF NOT EXISTS refresh_token (
    token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    issued_at TIMESTAMP NOT NULL DEFAULT now(),
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by_token_id UUID REFERENCES refresh_token(token_id),
    device_info VARCHAR(255),
    ip_address VARCHAR(45)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_token_hash ON refresh_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_token(user_id);

-- 3. Bảng tenant_memberships
CREATE TABLE IF NOT EXISTS tenant_memberships (
    membership_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL, -- Logical reference to order_db.tenants
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    membership_role VARCHAR(30) NOT NULL DEFAULT 'STAFF'
        CHECK (membership_role IN ('OWNER', 'STAFF', 'MANAGER', 'DRIVER')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'REVOKED')),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_user_membership UNIQUE (tenant_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_memberships_user ON tenant_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_memberships_tenant ON tenant_memberships(tenant_id);

-- 4. Bảng processed_events (Consumer Idempotency)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);

