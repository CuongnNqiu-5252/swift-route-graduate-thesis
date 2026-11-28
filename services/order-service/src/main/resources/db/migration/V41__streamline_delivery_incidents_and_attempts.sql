-- V41: Drop unused incident severity & call-count columns, remove ESCALATED status, and streamline failure reasons

UPDATE delivery_incidents
SET status = 'OPEN'
WHERE status = 'ESCALATED';

DROP INDEX IF EXISTS idx_incidents_severity;

ALTER TABLE delivery_incidents
    DROP COLUMN IF EXISTS severity;

ALTER TABLE delivery_incidents
    DROP CONSTRAINT IF EXISTS delivery_incidents_status_check;

ALTER TABLE delivery_incidents
    ADD CONSTRAINT delivery_incidents_status_check
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED'));

CREATE INDEX IF NOT EXISTS idx_incidents_tenant_status
    ON delivery_incidents(tenant_id, status, created_at DESC);

ALTER TABLE delivery_attempts
    DROP COLUMN IF EXISTS contact_attempts;
