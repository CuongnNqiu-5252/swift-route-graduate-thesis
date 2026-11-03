ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS package_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_package_count_check;
ALTER TABLE orders ADD CONSTRAINT orders_package_count_check CHECK (package_count > 0 AND package_count <= 100);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (
    status IN ('PENDING_PAYMENT', 'PAYMENT_FAILED', 'PAID', 'PLANNED', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'CANCELLED', 'RETURNED')
);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_fulfillment_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_fulfillment_status_check CHECK (
    fulfillment_status IN ('DRAFT', 'CONFIRMED', 'READY_FOR_PLANNING', 'PLANNED', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'CANCELLED', 'RETURNING', 'RETURNED')
);

CREATE TABLE package_units (
    package_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    package_number INTEGER NOT NULL,
    package_code VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PICKUP'
        CHECK (status IN ('PENDING_PICKUP', 'IN_CUSTODY', 'HANDOVER_PENDING', 'DELIVERED', 'RETURNING', 'RETURNED', 'DISPUTED')),
    current_custodian_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(order_id, package_number),
    UNIQUE(tenant_id, package_code)
);
CREATE INDEX idx_package_units_order ON package_units(order_id, package_number);
CREATE INDEX idx_package_units_custodian ON package_units(tenant_id, current_custodian_id, status)
    WHERE current_custodian_id IS NOT NULL;

CREATE TABLE package_handovers (
    handover_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    from_driver_id UUID NOT NULL,
    to_driver_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'SENDER_CONFIRMED', 'COMPLETED', 'CANCELLED', 'DISPUTED', 'EXPIRED')),
    reason VARCHAR(1000) NOT NULL,
    requested_by UUID NOT NULL,
    sender_confirmed_at TIMESTAMPTZ,
    receiver_confirmed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (from_driver_id <> to_driver_id)
);
CREATE INDEX idx_package_handovers_driver_status ON package_handovers(tenant_id, from_driver_id, to_driver_id, status, created_at DESC);

CREATE TABLE package_handover_items (
    handover_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    handover_id UUID NOT NULL REFERENCES package_handovers(handover_id) ON DELETE CASCADE,
    package_id UUID NOT NULL REFERENCES package_units(package_id),
    previous_status VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'TRANSFERRED', 'DISPUTED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(handover_id, package_id)
);
CREATE UNIQUE INDEX ux_package_active_handover ON package_handover_items(package_id)
    WHERE status = 'PENDING';

CREATE TABLE package_custody_events (
    custody_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id UUID NOT NULL REFERENCES package_units(package_id),
    tenant_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL
        CHECK (event_type IN ('PICKUP_CONFIRMED', 'HANDOVER_COMPLETED', 'DELIVERY_RELEASED', 'RETURN_STARTED', 'RETURN_RECEIVED', 'DISPUTE_OPENED')),
    from_custodian_id UUID,
    to_custodian_id UUID,
    handover_id UUID REFERENCES package_handovers(handover_id),
    actor_id UUID NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_custody_events_package ON package_custody_events(package_id, created_at DESC);
CREATE INDEX idx_custody_events_tenant ON package_custody_events(tenant_id, created_at DESC);

ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS return_task_id UUID REFERENCES delivery_tasks(task_id);
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS received_by UUID;
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ;
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS completion_notes VARCHAR(1000);
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS ux_return_requests_task ON return_requests(return_task_id) WHERE return_task_id IS NOT NULL;
