ALTER TABLE tenants
    ADD COLUMN owner_user_id UUID,
    ADD COLUMN pickup_lat DOUBLE PRECISION,
    ADD COLUMN pickup_lng DOUBLE PRECISION,
    ADD COLUMN approval_reason VARCHAR(500),
    ADD COLUMN approved_by UUID,
    ADD COLUMN approved_at TIMESTAMP,
    ADD COLUMN suspended_at TIMESTAMP,
    ADD COLUMN status_updated_at TIMESTAMP NOT NULL DEFAULT now(),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE tenants
    DROP CONSTRAINT IF EXISTS tenants_status_check;

ALTER TABLE tenants
    ADD CONSTRAINT tenants_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'));

CREATE UNIQUE INDEX ux_tenants_business_name_normalized
    ON tenants (LOWER(TRIM(business_name)));

CREATE INDEX idx_tenants_owner_status
    ON tenants (owner_user_id, status);
