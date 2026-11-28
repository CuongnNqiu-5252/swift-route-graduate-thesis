ALTER TABLE auth_notification_outbox ADD COLUMN IF NOT EXISTS event_type VARCHAR(100)
    NOT NULL DEFAULT 'user.email_verification.v1';
