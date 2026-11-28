-- ==============================================================================
-- order_db: V12__dispatch_plans.sql
-- Dispatch plan persistence for Order Service orchestration.
-- Plans are created by the Java PlanningTriggerScheduler, submitted to the
-- routing engine (POST /optimize/batch), and tracked through their lifecycle.
-- ==============================================================================

CREATE TABLE dispatch_plans (
    plan_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),

    -- Lifecycle state machine: DRAFT -> APPROVED -> DISPATCHED -> CANCELLED
    plan_status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (plan_status IN ('DRAFT', 'APPROVED', 'DISPATCHED', 'CANCELLED')),

    -- Driver this plan is assigned to (logical reference to driver-service)
    driver_id           UUID,

    -- Algorithm reported by routing engine
    algorithm_used      VARCHAR(60),

    -- Route geometry and stops returned by the routing engine (JSONB for flexibility)
    route_waypoints     JSONB,

    -- Summary metrics returned by routing engine
    total_distance_km   DECIMAL(10, 3),
    estimated_duration_minutes INTEGER,

    -- Trigger that created this plan
    trigger_type        VARCHAR(30)
        CHECK (trigger_type IN ('BATCH_SIZE', 'MAX_WAIT', 'SCHEDULED', 'URGENT', 'MANUAL')),

    -- Whether this plan required manual approval per tenant dispatch config
    requires_approval   BOOLEAN NOT NULL DEFAULT false,

    -- Acknowledgement tracking
    ack_deadline_at     TIMESTAMPTZ,
    acknowledged_at     TIMESTAMPTZ,
    declined_at         TIMESTAMPTZ,
    decline_reason      VARCHAR(500),

    -- Reassignment tracking
    reassignment_count  SMALLINT NOT NULL DEFAULT 0,

    -- Lifecycle timestamps
    planned_at          TIMESTAMPTZ,
    approved_at         TIMESTAMPTZ,
    approved_by         UUID,         -- Logical reference to auth_db.users
    dispatched_at       TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancel_reason       VARCHAR(500),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_dispatch_plans_tenant_status   ON dispatch_plans(tenant_id, plan_status);
CREATE INDEX idx_dispatch_plans_driver          ON dispatch_plans(driver_id) WHERE driver_id IS NOT NULL;
CREATE INDEX idx_dispatch_plans_created         ON dispatch_plans(tenant_id, created_at DESC);

-- Link delivery_tasks to their dispatch plan (nullable — task may exist before plan)
ALTER TABLE delivery_tasks
    ADD COLUMN IF NOT EXISTS dispatch_plan_id UUID;

CREATE INDEX idx_delivery_tasks_plan ON delivery_tasks(dispatch_plan_id) WHERE dispatch_plan_id IS NOT NULL;

-- Track which orders belong to a plan (many-to-many via link table)
CREATE TABLE dispatch_plan_orders (
    plan_id    UUID NOT NULL REFERENCES dispatch_plans(plan_id) ON DELETE CASCADE,
    order_id   UUID NOT NULL REFERENCES orders(order_id),
    stop_index INTEGER NOT NULL,  -- Optimised stop sequence position (0-based)
    PRIMARY KEY (plan_id, order_id)
);

CREATE INDEX idx_dispatch_plan_orders_order ON dispatch_plan_orders(order_id);

