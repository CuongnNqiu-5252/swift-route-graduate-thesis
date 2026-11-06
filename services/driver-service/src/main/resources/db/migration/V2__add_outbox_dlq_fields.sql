-- ==============================================================================
-- driver_db: V2__add_outbox_dlq_fields.sql
-- Add outbox retry, next attempt, and DLQ lifecycle fields
-- ==============================================================================

ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS dlq_published_at TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS last_error TEXT;

