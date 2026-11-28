-- Drop unused scope column from tenant_verification_requests and preserve
-- the one-open-verification-per-tenant partial unique index on tenant_id.
DROP INDEX IF EXISTS uq_tenant_open_verification;

ALTER TABLE tenant_verification_requests
    DROP COLUMN IF EXISTS scope;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_open_verification
    ON tenant_verification_requests (tenant_id)
    WHERE status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'CHANGES_REQUIRED')
      AND closed_at IS NULL;
