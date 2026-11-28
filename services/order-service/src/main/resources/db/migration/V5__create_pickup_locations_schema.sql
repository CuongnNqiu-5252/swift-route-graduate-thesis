-- ==============================================================================
-- order_db: V5__create_pickup_locations_schema.sql
-- Multi-pickup locations (stores, warehouses, dispatch hubs) per tenant
-- ==============================================================================

CREATE TABLE IF NOT EXISTS pickup_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    contact_name VARCHAR(100),
    contact_phone VARCHAR(50),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pickup_locations_tenant_id ON pickup_locations(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pickup_locations_status ON pickup_locations(status);

-- Partial unique index ensuring at most one active default pickup location per tenant
CREATE UNIQUE INDEX IF NOT EXISTS ux_pickup_locations_tenant_default
    ON pickup_locations(tenant_id)
    WHERE is_default = TRUE AND status = 'ACTIVE';

-- Add pickup_location_id reference to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS pickup_location_id UUID REFERENCES pickup_locations(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_orders_pickup_location_id ON orders(pickup_location_id);

