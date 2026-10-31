-- V1: Create drivers table
CREATE TABLE IF NOT EXISTS drivers
(
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id    UUID        NOT NULL, -- logical reference to order_service.tenants
    full_name    VARCHAR(255) NOT NULL,
    phone        VARCHAR(20)  NOT NULL UNIQUE,
    license_plate VARCHAR(20) NOT NULL UNIQUE,
    status       VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
