-- V2: Extend the 'drivers' table (created by V1) with all missing columns,
--     and create the driver_earning and driver_rating tables.
--
-- V1 created: drivers(id, tenant_id, full_name, phone, license_plate, status,
--                      created_at, updated_at)
-- This migration:
--   1. Adds all columns the Driver entity requires.
--   2. Renames the PK column from 'id' to 'driver_id' to match the entity mapping.
--   3. Adds a nullable user_id column for the Auth Service logical reference.
--   4. Creates driver_earning and driver_rating tables.

-- Step 1: Add missing operational columns
ALTER TABLE drivers
    ADD COLUMN IF NOT EXISTS user_id       UUID         UNIQUE,
    ADD COLUMN IF NOT EXISTS vehicle_type  VARCHAR(30)  NOT NULL DEFAULT 'MOTORCYCLE',
    ADD COLUMN IF NOT EXISTS capacity      INTEGER,
    ADD COLUMN IF NOT EXISTS shift_start   TIME,
    ADD COLUMN IF NOT EXISTS shift_end     TIME,
    ADD COLUMN IF NOT EXISTS driver_type   VARCHAR(30)  NOT NULL DEFAULT 'DEDICATED',
    ADD COLUMN IF NOT EXISTS current_lat   DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS current_lng   DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS is_available  BOOLEAN      NOT NULL DEFAULT FALSE;

-- Step 2: Add CHECK constraints to status column
ALTER TABLE drivers
    DROP CONSTRAINT IF EXISTS drivers_status_check;
ALTER TABLE drivers
    ADD CONSTRAINT drivers_status_check
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'));

-- Step 3: Rename PK column 'id' -> 'driver_id' to match the entity @Column(name="driver_id")
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'drivers' AND column_name = 'id'
    ) THEN
        ALTER TABLE drivers RENAME COLUMN id TO driver_id;
    END IF;
END $$;

-- Step 4: Add index on tenant for available driver lookup
CREATE INDEX IF NOT EXISTS idx_drivers_tenant_available ON drivers (tenant_id, is_available);
CREATE INDEX IF NOT EXISTS idx_drivers_user_id           ON drivers (user_id) WHERE user_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- driver_earning: records commission/payout per delivered order
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS driver_earning
(
    driver_earning_id UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id         UUID           NOT NULL,
    order_id          UUID           NOT NULL, -- logical ref → order_service.orders
    driver_id         UUID           NOT NULL, -- logical ref → drivers.driver_id
    gross_amount      NUMERIC(12, 2) NOT NULL,
    platform_fee      NUMERIC(12, 2) NOT NULL,
    net_amount        NUMERIC(12, 2) NOT NULL,
    payment_status    VARCHAR(30)    NOT NULL DEFAULT 'PENDING'
        CHECK (payment_status IN ('PENDING', 'PAID', 'FAILED')),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_driver_earning_driver_id ON driver_earning (driver_id);
CREATE INDEX IF NOT EXISTS idx_driver_earning_order_id  ON driver_earning (order_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- driver_rating: customer ratings per delivery
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS driver_rating
(
    driver_rating_id UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    driver_id        UUID        NOT NULL, -- logical ref → drivers.driver_id
    order_id         UUID,                 -- logical ref → order_service.orders
    customer_id      UUID,                 -- logical ref → auth_service.users
    rating           SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review           VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_driver_rating_driver_id ON driver_rating (driver_id);
