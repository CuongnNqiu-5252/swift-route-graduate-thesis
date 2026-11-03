ALTER TABLE tenants ADD COLUMN contact_phone VARCHAR(20);
ALTER TABLE tenants ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE tenants ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'VN';
ALTER TABLE tenants ADD COLUMN delivery_mode VARCHAR(20) NOT NULL DEFAULT 'OWN_FLEET'
    CHECK (delivery_mode IN ('OWN_FLEET','SHARED_POOL','HYBRID'));
ALTER TABLE tenants ADD COLUMN delivery_settings JSONB NOT NULL DEFAULT '{"preparation_enabled":false,"preparation_minutes":0,"delivery_verification_required":true,"sensitive_handling":false}';
ALTER TABLE tenants ADD COLUMN settings_confirmed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE tenants ADD COLUMN mode_confirmed BOOLEAN NOT NULL DEFAULT false;
-- Legacy approval remains valid and subscription metadata is preserved.
UPDATE tenants SET settings_confirmed=true,mode_confirmed=true WHERE status='APPROVED';
CREATE TABLE tenant_registration_keys (
    owner_user_id UUID NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),request_payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),PRIMARY KEY(owner_user_id,idempotency_key)
);
CREATE TABLE onboarding_progress (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(tenant_id),
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED' CHECK(status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED')),
    completed_steps JSONB NOT NULL DEFAULT '[]',updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO onboarding_progress(tenant_id) SELECT tenant_id FROM tenants;
CREATE TABLE tenant_verification_requests (
    request_id UUID PRIMARY KEY,tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    scope VARCHAR(50) NOT NULL DEFAULT 'INITIAL_BUSINESS_APPROVAL',
    status VARCHAR(30) NOT NULL CHECK(status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','CHANGES_REQUIRED','APPROVED','REJECTED')),
    submission_key VARCHAR(128) NOT NULL, submitted_by UUID NOT NULL,submitted_at TIMESTAMPTZ,
    snapshot JSONB NOT NULL,reviewer_id UUID,reviewed_at TIMESTAMPTZ,reason VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),closed_at TIMESTAMPTZ,
    UNIQUE(tenant_id,submission_key)
);
CREATE UNIQUE INDEX uq_tenant_open_verification ON tenant_verification_requests(tenant_id,scope)
    WHERE status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','CHANGES_REQUIRED') AND closed_at IS NULL;
CREATE INDEX idx_verification_review_queue ON tenant_verification_requests(status,created_at DESC);
