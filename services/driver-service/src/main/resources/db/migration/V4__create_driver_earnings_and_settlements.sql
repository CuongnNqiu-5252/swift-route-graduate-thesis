CREATE TABLE IF NOT EXISTS earning_policy (
    tenant_id UUID PRIMARY KEY,
    base_amount NUMERIC(12, 0) NOT NULL DEFAULT 12000 CHECK (base_amount >= 0),
    per_km_amount NUMERIC(12, 0) NOT NULL DEFAULT 3000 CHECK (per_km_amount >= 0),
    minimum_amount NUMERIC(12, 0) NOT NULL DEFAULT 15000 CHECK (minimum_amount >= 0),
    heavy_weight_threshold_kg NUMERIC(8, 2) NOT NULL DEFAULT 5 CHECK (heavy_weight_threshold_kg > 0),
    heavy_bonus_amount NUMERIC(12, 0) NOT NULL DEFAULT 2000 CHECK (heavy_bonus_amount >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS driver_settlement (
    settlement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    driver_id UUID NOT NULL REFERENCES drivers(driver_id),
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'PAID', 'CANCELLED')),
    earning_count INTEGER NOT NULL DEFAULT 0 CHECK (earning_count >= 0),
    total_amount NUMERIC(12, 0) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    paid_by UUID,
    paid_at TIMESTAMPTZ,
    payment_reference VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (period_end > period_start)
);

CREATE INDEX IF NOT EXISTS idx_settlement_tenant_created ON driver_settlement(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_settlement_driver_period ON driver_settlement(driver_id, period_start, period_end);

ALTER TABLE driver_earning
    ALTER COLUMN gross_amount SET DEFAULT 0,
    ALTER COLUMN platform_fee SET DEFAULT 0,
    ALTER COLUMN net_amount SET DEFAULT 0,
    ALTER COLUMN payment_status DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS delivery_task_id UUID,
    ADD COLUMN IF NOT EXISTS earning_type VARCHAR(20) NOT NULL DEFAULT 'DELIVERY',
    ADD COLUMN IF NOT EXISTS delivery_fee NUMERIC(12, 0) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payable_distance_km NUMERIC(8, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS base_amount NUMERIC(12, 0) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS distance_amount NUMERIC(12, 0) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bonus_amount NUMERIC(12, 0) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS adjustment_amount NUMERIC(12, 0) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS calculation_snapshot TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS settlement_id UUID REFERENCES driver_settlement(settlement_id);

UPDATE driver_earning SET net_amount = 0 WHERE net_amount IS NULL;
ALTER TABLE driver_earning ALTER COLUMN net_amount SET NOT NULL;
ALTER TABLE driver_earning DROP CONSTRAINT IF EXISTS driver_earning_status_check;
ALTER TABLE driver_earning ADD CONSTRAINT driver_earning_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'PAID', 'CANCELLED'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_earning_delivery_task ON driver_earning(delivery_task_id) WHERE delivery_task_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_driver_earning_tenant_status ON driver_earning(tenant_id, status, completed_at DESC);
CREATE INDEX IF NOT EXISTS idx_driver_earning_settlement ON driver_earning(settlement_id);
