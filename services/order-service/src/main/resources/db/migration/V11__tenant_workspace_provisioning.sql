-- Apply before deploying the corresponding application version. No cross-service FK.
CREATE TABLE tenant_provisioning (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(tenant_id),
    owner_user_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PROVISIONING'
        CHECK (status IN ('PROVISIONING','READY','RETRY_REQUIRED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ready_at TIMESTAMPTZ,
    CHECK ((status = 'READY') = (ready_at IS NOT NULL))
);
-- Existing tenants are reconciled with Auth before becoming usable under the new guard.
INSERT INTO tenant_provisioning(tenant_id,owner_user_id)
SELECT tenant_id,owner_user_id FROM tenants WHERE owner_user_id IS NOT NULL;
CREATE INDEX idx_tenant_provisioning_retry ON tenant_provisioning(next_attempt_at)
    WHERE status <> 'READY';
CREATE INDEX idx_tenant_verification_queue ON tenant_verification_requests(status,created_at DESC);
