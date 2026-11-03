ALTER TABLE notification_deliveries ADD COLUMN recipient_email VARCHAR(255);
ALTER TABLE notification_deliveries ADD COLUMN encrypted_body TEXT;
ALTER TABLE notification_deliveries ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE notification_deliveries ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX idx_email_delivery_retry ON notification_deliveries(next_attempt_at) WHERE status IN ('PENDING','FAILED');
