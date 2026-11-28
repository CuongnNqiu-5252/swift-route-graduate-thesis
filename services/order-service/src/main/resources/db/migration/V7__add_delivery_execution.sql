CREATE TABLE delivery_tasks (
    task_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    task_type VARCHAR(20) NOT NULL CHECK (task_type IN ('PICKUP','DELIVERY','RETURN')),
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED' CHECK (status IN ('ASSIGNED','ACCEPTED','ARRIVED','PICKED_UP','OUT_FOR_DELIVERY','COMPLETED','FAILED','CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(order_id, task_type)
);
CREATE INDEX idx_delivery_tasks_driver_status ON delivery_tasks(driver_id, status);

CREATE TABLE delivery_attempts (
    attempt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    task_id UUID REFERENCES delivery_tasks(task_id) ON DELETE SET NULL,
    tenant_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS','FAILED')),
    failure_reason VARCHAR(100),
    notes VARCHAR(500),
    proof_url TEXT,
    completed_by UUID NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(order_id, attempt_number)
);

CREATE TABLE audit_logs (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    actor_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_tenant_aggregate ON audit_logs(tenant_id, aggregate_type, aggregate_id, created_at DESC);
