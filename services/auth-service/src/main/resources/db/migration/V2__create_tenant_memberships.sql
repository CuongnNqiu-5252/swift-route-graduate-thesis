CREATE TABLE tenant_memberships (
    membership_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(user_id),
    membership_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ux_tenant_memberships_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_tenant_memberships_user_status
    ON tenant_memberships (user_id, status);
