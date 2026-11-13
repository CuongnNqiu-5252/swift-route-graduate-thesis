-- ==============================================================================
-- auth_db: V2__extend_tenant_membership_status_and_ordering.sql
-- Extend tenant_memberships status constraint and add event version tracking
-- ==============================================================================

ALTER TABLE tenant_memberships DROP CONSTRAINT IF EXISTS tenant_memberships_status_check;
ALTER TABLE tenant_memberships ADD CONSTRAINT tenant_memberships_status_check
    CHECK (status IN ('ACTIVE', 'PENDING', 'INACTIVE', 'SUSPENDED', 'REVOKED'));

ALTER TABLE tenant_memberships ADD COLUMN IF NOT EXISTS last_event_version BIGINT DEFAULT 0;
ALTER TABLE tenant_memberships ADD COLUMN IF NOT EXISTS last_event_occurred_at TIMESTAMPTZ;

