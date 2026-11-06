-- ==============================================================================
-- driver_db: V1__init_driver_schema.sql
-- Consolidated baseline schema for Driver Service
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Table: drivers
CREATE TABLE IF NOT EXISTS drivers (
    driver_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE, -- Logical reference to auth_db.users
    tenant_id UUID NOT NULL, -- Tenant Discriminator (Single-Tenant)
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL UNIQUE,
    license_plate VARCHAR(20) NOT NULL UNIQUE,
    vehicle_type VARCHAR(30) NOT NULL
        CHECK (vehicle_type IN ('MOTORCYCLE', 'VAN', 'TRUCK', 'MOTORBIKE')),
    capacity INTEGER,
    shift_start TIME,
    shift_end TIME,
    driver_type VARCHAR(30) NOT NULL DEFAULT 'DEDICATED'
        CHECK (driver_type IN ('INTERNAL', 'CROWDSOURCED', 'THIRD_PARTY', 'DEDICATED')),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'OFFLINE', 'INACTIVE', 'AVAILABLE')),
    approval_status VARCHAR(30) DEFAULT 'APPROVED',
    is_available BOOLEAN NOT NULL DEFAULT FALSE,
    current_lat DOUBLE PRECISION,
    current_lng DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_drivers_tenant_available ON drivers(tenant_id, is_available);
CREATE INDEX IF NOT EXISTS idx_drivers_phone ON drivers(phone);
CREATE INDEX IF NOT EXISTS idx_drivers_license_plate ON drivers(license_plate);
CREATE INDEX IF NOT EXISTS idx_drivers_user_id ON drivers(user_id) WHERE user_id IS NOT NULL;

-- 2. Table: driver_earning
CREATE TABLE IF NOT EXISTS driver_earning (
    driver_earning_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL UNIQUE, -- Logical reference to order_db.orders
    driver_id UUID NOT NULL REFERENCES drivers(driver_id),
    gross_amount NUMERIC(12, 0) NOT NULL,
    driver_share NUMERIC(12, 0) NOT NULL DEFAULT 0,
    platform_fee NUMERIC(12, 0) NOT NULL DEFAULT 0,
    net_amount NUMERIC(12, 0),
    status VARCHAR(30) DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SETTLED', 'CANCELLED', 'PAID', 'FAILED')),
    payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (payment_status IN ('PENDING', 'PAID', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_earning_driver_tenant ON driver_earning(driver_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_driver_earning_order_id ON driver_earning(order_id);

-- 3. Table: driver_rating
CREATE TABLE IF NOT EXISTS driver_rating (
    driver_rating_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    driver_id UUID NOT NULL REFERENCES drivers(driver_id),
    order_id UUID,
    customer_id UUID,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    score INTEGER CHECK (score BETWEEN 1 AND 5),
    review VARCHAR(500),
    comment VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ratings_driver ON driver_rating(driver_id);

-- 4. Table: outbox_events
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

CREATE INDEX IF NOT EXISTS idx_driver_outbox_unpublished ON outbox_events(created_at ASC) WHERE published_at IS NULL;

-- 5. Table: processed_events (Consumer Idempotency)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);

