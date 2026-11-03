-- ==============================================================================
-- order_db: V1__init_order_schema.sql
-- Consolidated baseline schema for Order Service
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Table: tenants
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name VARCHAR(255) NOT NULL,
    owner_user_id UUID, -- Logical reference to auth_db.users
    business_type VARCHAR(50) NOT NULL DEFAULT 'OTHER'
        CHECK (business_type IN ('RESTAURANT', 'GROCERY', 'PHARMACY', 'RETAIL', 'LOGISTICS', 'OTHER', 'CAFE')),
    address VARCHAR(500),
    pickup_lat DOUBLE PRECISION,
    pickup_lng DOUBLE PRECISION,
    optimization_weights JSONB NOT NULL DEFAULT '{"distance": 0.5, "time": 0.5}'::jsonb,
    dispatch_config JSONB NOT NULL DEFAULT '{
        "auto_dispatch_enabled": true,
        "batch_size_threshold": 5,
        "max_wait_time_seconds": 300,
        "urgent_order_mode": "IMMEDIATE_SINGLE",
        "require_approval": false,
        "max_orders_per_driver": 5,
        "algorithm": "auto"
    }'::jsonb,
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'FREE_TRIAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'ACTIVE', 'SUSPENDED', 'REJECTED')),
    approval_reason VARCHAR(500),
    approved_by UUID,
    approved_at TIMESTAMP,
    suspended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    status_updated_at TIMESTAMP NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tenants_business_name_normalized
    ON tenants (LOWER(TRIM(business_name)));
CREATE INDEX IF NOT EXISTS idx_tenants_owner ON tenants(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);

-- 2. Table: orders
CREATE TABLE IF NOT EXISTS orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    customer_id UUID NOT NULL, -- Logical reference to auth_db.users
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    pickup_address VARCHAR(500),
    delivery_lat DOUBLE PRECISION NOT NULL,
    delivery_lng DOUBLE PRECISION NOT NULL,
    delivery_address VARCHAR(500),
    time_window_start TIMESTAMPTZ NOT NULL,
    time_window_end TIMESTAMPTZ NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    package_category VARCHAR(30) NOT NULL DEFAULT 'PARCEL',
    item_description VARCHAR(255),
    notes VARCHAR(500),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('NORMAL', 'HIGH', 'URGENT')),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT'
        CHECK (status IN ('PENDING_PAYMENT', 'PAYMENT_FAILED', 'PAID', 'PLANNED', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'CANCELLED')),
    fulfillment_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (fulfillment_status IN ('DRAFT', 'CONFIRMED', 'READY_FOR_PLANNING', 'PLANNED', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'CANCELLED')),
    payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (payment_status IN ('UNPAID', 'PENDING', 'PAID', 'FAILED', 'COD_PENDING', 'COLLECTED', 'PARTIALLY_COLLECTED', 'REFUNDED')),
    total_amount NUMERIC(12, 0) NOT NULL DEFAULT 0
        CHECK (total_amount >= 0),
    assigned_driver_id UUID, -- Logical reference to driver_db.drivers
    plan_id UUID,            -- Logical reference to routing_db.routing_plans
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT orders_time_window_check CHECK (time_window_end > time_window_start)
);

CREATE INDEX IF NOT EXISTS idx_orders_tenant_status ON orders(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_fulfillment ON orders(tenant_id, fulfillment_status);
CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_driver ON orders(assigned_driver_id) WHERE assigned_driver_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_plan ON orders(plan_id) WHERE plan_id IS NOT NULL;

-- 3. Table: order_items
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id UUID, -- Logical reference to product-service
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(100),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 0) NOT NULL CHECK (unit_price >= 0),
    line_total NUMERIC(12, 0) NOT NULL CHECK (line_total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items(product_id) WHERE product_id IS NOT NULL;

-- 4. Table: order_status_history
CREATE TABLE IF NOT EXISTS order_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by_user_id UUID,
    changed_by_role VARCHAR(30),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_order_history_order ON order_status_history(order_id, created_at DESC);

-- 5. Table: outbox_events
CREATE TABLE IF NOT EXISTS outbox_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_order_outbox_unpublished ON outbox_events(created_at ASC) WHERE published_at IS NULL;

-- 6. Table: processed_events (Consumer Idempotency)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);

