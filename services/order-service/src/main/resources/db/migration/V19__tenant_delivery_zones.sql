-- V19: Tenant delivery service area configuration (single circle zone per tenant)
CREATE TABLE tenant_delivery_zones (
    zone_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    zone_type VARCHAR(30) NOT NULL DEFAULT 'CIRCLE'
        CHECK (zone_type IN ('CIRCLE')),
    center_lat DOUBLE PRECISION NOT NULL
        CHECK (center_lat >= -90.0 AND center_lat <= 90.0),
    center_lng DOUBLE PRECISION NOT NULL
        CHECK (center_lng >= -180.0 AND center_lng <= 180.0),
    radius_km DOUBLE PRECISION NOT NULL
        CHECK (radius_km >= 0.5 AND radius_km <= 100.0),
    enforcement_mode VARCHAR(20) NOT NULL DEFAULT 'STRICT_BLOCK'
        CHECK (enforcement_mode IN ('STRICT_BLOCK', 'WARN_ONLY')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id)
);

CREATE INDEX idx_tenant_delivery_zones_tenant_active
    ON tenant_delivery_zones(tenant_id)
    WHERE is_active = true;
