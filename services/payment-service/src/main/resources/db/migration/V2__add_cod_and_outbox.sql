-- ==============================================================================
-- payment_db: V2__add_cod_and_outbox.sql
-- Add COD support, tenant_id constraint, outbox_events, and processed_events
-- ==============================================================================

-- 1. Modify expires_at to allow NULL (for COD payments)
ALTER TABLE payments ALTER COLUMN expires_at DROP NOT NULL;

-- 2. Ensure tenant_id is NOT NULL (defaulting any orphaned records to default tenant if applicable)
UPDATE payments
SET tenant_id = 'a1111111-1111-1111-1111-111111111111'
WHERE tenant_id IS NULL;

ALTER TABLE payments ALTER COLUMN tenant_id SET NOT NULL;

-- 3. Extend status CHECK constraint to support COD statuses
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED', 'COD_PENDING', 'COLLECTED', 'PARTIALLY_COLLECTED'));

-- 4. Index for tenant and status queries
CREATE INDEX IF NOT EXISTS idx_payments_tenant_status ON payments(tenant_id, status);

-- 5. Outbox events table for Payment Service
CREATE TABLE IF NOT EXISTS outbox_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_payment_outbox_unpublished
    ON outbox_events(created_at ASC)
    WHERE published_at IS NULL;

-- 6. Processed events table (Consumer Idempotency)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);

