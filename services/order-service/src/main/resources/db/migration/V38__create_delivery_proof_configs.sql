CREATE TABLE IF NOT EXISTS delivery_proof_configs (
    config_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL UNIQUE,
    success_note_policy      VARCHAR(20) NOT NULL DEFAULT 'OPTIONAL',
    success_signature_policy VARCHAR(20) NOT NULL DEFAULT 'DISABLED',
    success_photo_policy     VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    failed_note_policy       VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    failed_signature_policy  VARCHAR(20) NOT NULL DEFAULT 'DISABLED',
    failed_photo_policy      VARCHAR(20) NOT NULL DEFAULT 'OPTIONAL',
    version                  INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_delivery_proof_config_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    CONSTRAINT chk_success_note_policy
        CHECK (success_note_policy IN ('DISABLED', 'OPTIONAL', 'REQUIRED')),
    CONSTRAINT chk_success_signature_policy
        CHECK (success_signature_policy IN ('DISABLED', 'OPTIONAL', 'REQUIRED')),
    CONSTRAINT chk_success_photo_policy
        CHECK (success_photo_policy IN ('DISABLED', 'OPTIONAL', 'REQUIRED')),
    CONSTRAINT chk_failed_note_policy
        CHECK (failed_note_policy IN ('DISABLED', 'OPTIONAL', 'REQUIRED')),
    CONSTRAINT chk_failed_signature_policy
        CHECK (failed_signature_policy IN ('DISABLED', 'OPTIONAL', 'REQUIRED')),
    CONSTRAINT chk_failed_photo_policy
        CHECK (failed_photo_policy IN ('DISABLED', 'OPTIONAL', 'REQUIRED'))
);
