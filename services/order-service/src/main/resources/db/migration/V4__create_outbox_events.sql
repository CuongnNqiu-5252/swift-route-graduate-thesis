CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
