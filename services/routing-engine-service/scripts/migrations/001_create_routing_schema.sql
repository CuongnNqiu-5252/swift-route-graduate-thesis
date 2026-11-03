-- ==============================================================================
-- routing_db: 001_create_routing_schema.sql
-- Baseline PostgreSQL schema for Routing Engine Service
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Table: optimization_jobs
CREATE TABLE IF NOT EXISTS optimization_jobs (
    job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    shift VARCHAR(20) NOT NULL,
    algorithm_requested VARCHAR(50) NOT NULL,
    algorithm_used VARCHAR(50) NOT NULL,
    total_orders INTEGER NOT NULL DEFAULT 0,
    total_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_duration_minutes DOUBLE PRECISION NOT NULL DEFAULT 0,
    execution_time_ms DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_opt_jobs_tenant ON optimization_jobs(tenant_id, created_at DESC);

-- 2. Table: routing_plans
CREATE TABLE IF NOT EXISTS routing_plans (
    plan_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES optimization_jobs(job_id) ON DELETE SET NULL,
    tenant_id UUID NOT NULL,
    shift VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'DISPATCHED', 'COMPLETED', 'CANCELLED')),
    algorithm_used VARCHAR(50) NOT NULL,
    total_orders INTEGER NOT NULL DEFAULT 0,
    total_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_duration_minutes DOUBLE PRECISION NOT NULL DEFAULT 0,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatched_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_plans_tenant_status ON routing_plans(tenant_id, status);

-- 3. Table: driver_routes
CREATE TABLE IF NOT EXISTS driver_routes (
    route_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES routing_plans(plan_id) ON DELETE CASCADE,
    driver_id UUID NOT NULL, -- Logical reference to driver_db.drivers
    driver_name VARCHAR(255),
    total_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_duration_minutes DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED'
        CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_driver_routes_plan ON driver_routes(plan_id);
CREATE INDEX IF NOT EXISTS idx_driver_routes_driver ON driver_routes(driver_id);

-- 4. Table: route_stops
CREATE TABLE IF NOT EXISTS route_stops (
    stop_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id UUID NOT NULL REFERENCES driver_routes(route_id) ON DELETE CASCADE,
    order_id UUID NOT NULL, -- Logical reference to order_db.orders
    stop_index INTEGER NOT NULL,
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    pickup_address VARCHAR(500),
    delivery_lat DOUBLE PRECISION NOT NULL,
    delivery_lng DOUBLE PRECISION NOT NULL,
    delivery_address VARCHAR(500),
    demand_kg DOUBLE PRECISION NOT NULL DEFAULT 0,
    time_window_start TIMESTAMPTZ,
    time_window_end TIMESTAMPTZ,
    cumulative_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0,
    estimated_arrival_minutes DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ARRIVED', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_route_stop_index UNIQUE (route_id, stop_index)
);

CREATE INDEX IF NOT EXISTS idx_route_stops_route_index ON route_stops(route_id, stop_index ASC);
CREATE INDEX IF NOT EXISTS idx_route_stops_order ON route_stops(order_id);

-- 5. Table: pending_orders
CREATE TABLE IF NOT EXISTS pending_orders (
    order_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID,
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    pickup_address VARCHAR(500),
    delivery_lat DOUBLE PRECISION NOT NULL,
    delivery_lng DOUBLE PRECISION NOT NULL,
    delivery_address VARCHAR(500),
    weight_kg DOUBLE PRECISION NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    time_window_start TIMESTAMPTZ,
    time_window_end TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'PAID'
        CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'OPTIMIZING', 'DISPATCHED', 'CANCELLED')),
    shift VARCHAR(20) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pending_orders_tenant_status ON pending_orders(tenant_id, status);

-- 6. Table: processed_events (Consumer Idempotency)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);
