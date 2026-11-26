-- V11__driver_devices_and_email.sql
-- Optional contact email on driver profiles and registered mobile app devices (single active device per driver per tenant)

ALTER TABLE drivers
    ADD COLUMN IF NOT EXISTS email VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_drivers_tenant_email
    ON drivers(tenant_id, lower(email))
    WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS driver_devices (
    device_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    driver_id UUID NOT NULL REFERENCES drivers(driver_id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    installation_id VARCHAR(128) NOT NULL,
    session_id UUID NOT NULL UNIQUE,
    device_os VARCHAR(30),
    os_version VARCHAR(50),
    device_model VARCHAR(120),
    app_version VARCHAR(50),
    activated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_driver_devices_one_active
    ON driver_devices(tenant_id, driver_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_driver_devices_tenant_driver_latest
    ON driver_devices(tenant_id, driver_id, activated_at DESC);

CREATE INDEX IF NOT EXISTS idx_driver_devices_session
    ON driver_devices(session_id);
