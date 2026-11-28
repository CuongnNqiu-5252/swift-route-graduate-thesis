-- ==============================================================================
-- notification_db: V1__init_notification_schema.sql
-- Baseline schema for Notification Service
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Table: notification_preferences
CREATE TABLE IF NOT EXISTS notification_preferences (
    preference_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE, -- Logical reference to auth_db.users
    tenant_id UUID,
    order_updates BOOLEAN NOT NULL DEFAULT TRUE,
    promotions BOOLEAN NOT NULL DEFAULT FALSE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notif_pref_user ON notification_preferences(user_id);

-- 2. Table: device_tokens
CREATE TABLE IF NOT EXISTS device_tokens (
    token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL, -- Logical reference to auth_db.users
    device_type VARCHAR(20) NOT NULL CHECK (device_type IN ('ANDROID', 'IOS', 'WEB')),
    fcm_token VARCHAR(500) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_fcm ON device_tokens(fcm_token);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON device_tokens(user_id, is_active);

-- 3. Table: notification_deliveries
CREATE TABLE IF NOT EXISTS notification_deliveries (
    delivery_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    user_id UUID NOT NULL, -- Logical reference to auth_db.users
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('PUSH', 'EMAIL', 'SMS', 'IN_APP')),
    template_code VARCHAR(100),
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    payload JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    provider_response TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notif_deliveries_user ON notification_deliveries(user_id, created_at DESC);

-- 4. Table: processed_events (Consumer Idempotency)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);

