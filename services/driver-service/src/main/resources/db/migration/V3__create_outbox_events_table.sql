-- V3: Create outbox_events table for RabbitMQ messaging

CREATE TABLE IF NOT EXISTS outbox_events
(
    event_id       UUID         NOT NULL PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    retry_count    INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_pending ON outbox_events (created_at) WHERE published_at IS NULL;
