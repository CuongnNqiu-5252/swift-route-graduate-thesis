-- ==============================================================================
-- order_db: V2__add_outbox_and_amount_verification.sql
-- Add outbox retry/DLQ fields and amount_verification_status for historical orders
-- ==============================================================================

ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS dlq_published_at TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS last_error TEXT;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS amount_verification_status VARCHAR(30) NOT NULL DEFAULT 'VERIFIED';
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_amount_verification_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_amount_verification_status_check
    CHECK (amount_verification_status IN ('VERIFIED', 'UNVERIFIED_HISTORICAL', 'PENDING_AUDIT'));

UPDATE orders
SET amount_verification_status = 'UNVERIFIED_HISTORICAL'
WHERE notes LIKE '%Legacy order migrated without payment record%'
   OR (total_amount = 0 AND notes LIKE '%Legacy%');

