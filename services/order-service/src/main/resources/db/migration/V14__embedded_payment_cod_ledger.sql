-- Resource-conscious Payment/COD ledger embedded in order_db.
-- No extra database, outbox or inbox is required. Existing cod_collections rows
-- are retained and backfilled into the richer model.

CREATE TABLE payments (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    expected_amount NUMERIC(12,0) NOT NULL CHECK (expected_amount >= 0),
    collected_total NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (collected_total >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    mode VARCHAR(20) NOT NULL DEFAULT 'COD' CHECK (mode IN ('COD','ONLINE','QR','INTERNAL')),
    status VARCHAR(30) NOT NULL DEFAULT 'COD_PENDING'
        CHECK (status IN ('COD_PENDING','PARTIALLY_COLLECTED','COLLECTED','OVER_COLLECTED','CANCELLED','REFUNDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payments_tenant_order UNIQUE (tenant_id, order_id)
);

CREATE INDEX idx_payments_tenant_status ON payments(tenant_id, status, updated_at DESC);

INSERT INTO payments (order_id, tenant_id, expected_amount, collected_total, status, created_at, updated_at)
SELECT o.order_id,
       o.tenant_id,
       o.cod_amount,
       COALESCE((SELECT SUM(c.collected_amount)
                 FROM cod_collections c
                 WHERE c.order_id = o.order_id AND c.status <> 'DISCREPANCY'), 0),
       CASE
           WHEN COALESCE((SELECT SUM(c.collected_amount) FROM cod_collections c WHERE c.order_id = o.order_id AND c.status <> 'DISCREPANCY'), 0) = 0
               THEN 'COD_PENDING'
           WHEN COALESCE((SELECT SUM(c.collected_amount) FROM cod_collections c WHERE c.order_id = o.order_id AND c.status <> 'DISCREPANCY'), 0) < o.cod_amount
               THEN 'PARTIALLY_COLLECTED'
           WHEN COALESCE((SELECT SUM(c.collected_amount) FROM cod_collections c WHERE c.order_id = o.order_id AND c.status <> 'DISCREPANCY'), 0) = o.cod_amount
               THEN 'COLLECTED'
           ELSE 'OVER_COLLECTED'
       END,
       o.created_at,
       now()
FROM orders o
WHERE o.cod_amount > 0
ON CONFLICT (tenant_id, order_id) DO NOTHING;

ALTER TABLE cod_collections DROP CONSTRAINT IF EXISTS cod_collections_order_id_key;
ALTER TABLE cod_collections ADD COLUMN payment_id UUID REFERENCES payments(payment_id);
ALTER TABLE cod_collections ADD COLUMN operation_id UUID;
ALTER TABLE cod_collections ADD COLUMN outcome VARCHAR(30);
ALTER TABLE cod_collections ADD COLUMN reason VARCHAR(500);
ALTER TABLE cod_collections ADD COLUMN evidence_url TEXT;
ALTER TABLE cod_collections ADD COLUMN occurred_at TIMESTAMPTZ;
ALTER TABLE cod_collections ADD COLUMN received_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE cod_collections ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE cod_collections ADD COLUMN reversed_at TIMESTAMPTZ;
ALTER TABLE cod_collections ADD COLUMN reversal_reason VARCHAR(500);
ALTER TABLE cod_collections ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE cod_collections c
SET payment_id = p.payment_id,
    operation_id = COALESCE(c.operation_id, gen_random_uuid()),
    outcome = CASE
        WHEN c.collected_amount = 0 THEN 'NOT_COLLECTED'
        WHEN c.collected_amount < p.expected_amount THEN 'PARTIAL'
        WHEN c.collected_amount = p.expected_amount THEN 'FULL'
        ELSE 'OVER'
    END,
    occurred_at = COALESCE(c.occurred_at, c.collected_at),
    status = CASE
        WHEN c.status = 'VERIFIED' THEN 'HANDED_OVER'
        ELSE 'IN_CUSTODY'
    END
FROM payments p
WHERE p.order_id = c.order_id AND p.tenant_id = c.tenant_id;

ALTER TABLE cod_collections ALTER COLUMN payment_id SET NOT NULL;
ALTER TABLE cod_collections ALTER COLUMN operation_id SET NOT NULL;
ALTER TABLE cod_collections ALTER COLUMN outcome SET NOT NULL;
ALTER TABLE cod_collections ALTER COLUMN occurred_at SET NOT NULL;

ALTER TABLE cod_collections ADD CONSTRAINT cod_collections_status_check
    CHECK (status IN ('IN_CUSTODY','LOCKED_HANDOVER','HANDED_OVER','REVERSED'));
ALTER TABLE cod_collections ADD CONSTRAINT cod_collections_outcome_check
    CHECK (outcome IN ('FULL','PARTIAL','NOT_COLLECTED','OVER'));
ALTER TABLE cod_collections ADD CONSTRAINT uq_cod_collection_operation UNIQUE (tenant_id, operation_id);

CREATE INDEX idx_cod_collection_payment ON cod_collections(payment_id, collected_at);
CREATE INDEX idx_cod_collection_driver_custody ON cod_collections(tenant_id, driver_id, status, collected_at DESC);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check
    CHECK (payment_status IN ('UNPAID','PENDING','PAID','FAILED','COD_PENDING','COLLECTED','PARTIALLY_COLLECTED','OVER_COLLECTED','REFUNDED'));

CREATE TABLE cod_handover_batches (
    handover_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    driver_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED'
        CHECK (status IN ('DRAFT','SUBMITTED','VERIFIED','DISCREPANCY','RESOLVED','CANCELLED')),
    ledger_total NUMERIC(12,0) NOT NULL CHECK (ledger_total >= 0),
    declared_total NUMERIC(12,0) NOT NULL CHECK (declared_total >= 0),
    counted_total NUMERIC(12,0) CHECK (counted_total >= 0),
    discrepancy_amount NUMERIC(12,0),
    notes VARCHAR(1000),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_by UUID,
    verified_at TIMESTAMPTZ,
    verification_operation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_cod_handover_operation UNIQUE (tenant_id, operation_id)
);

CREATE UNIQUE INDEX uq_cod_handover_verification_operation
    ON cod_handover_batches(tenant_id, verification_operation_id)
    WHERE verification_operation_id IS NOT NULL;

CREATE INDEX idx_cod_handover_tenant_status ON cod_handover_batches(tenant_id, status, submitted_at DESC);
CREATE INDEX idx_cod_handover_driver ON cod_handover_batches(tenant_id, driver_id, submitted_at DESC);

CREATE TABLE cod_handover_lines (
    line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    handover_id UUID NOT NULL REFERENCES cod_handover_batches(handover_id) ON DELETE CASCADE,
    collection_id UUID NOT NULL REFERENCES cod_collections(collection_id),
    amount NUMERIC(12,0) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cod_handover_collection UNIQUE (collection_id)
);

CREATE INDEX idx_cod_handover_lines_batch ON cod_handover_lines(handover_id);

CREATE TABLE cod_disputes (
    dispute_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    payment_id UUID REFERENCES payments(payment_id),
    handover_id UUID REFERENCES cod_handover_batches(handover_id),
    order_id UUID REFERENCES orders(order_id),
    driver_id UUID NOT NULL,
    dispute_type VARCHAR(40) NOT NULL
        CHECK (dispute_type IN ('COLLECTION_SHORT','COLLECTION_OVER','NOT_COLLECTED','HANDOVER_SHORT','HANDOVER_OVER')),
    expected_amount NUMERIC(12,0) NOT NULL,
    actual_amount NUMERIC(12,0) NOT NULL,
    difference_amount NUMERIC(12,0) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED','CANCELLED')),
    reason VARCHAR(1000),
    evidence_url TEXT,
    resolution_type VARCHAR(40),
    adjustment_amount NUMERIC(12,0),
    resolution_notes VARCHAR(1000),
    resolved_by UUID,
    resolved_at TIMESTAMPTZ,
    resolution_operation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (payment_id IS NOT NULL OR handover_id IS NOT NULL)
);

CREATE UNIQUE INDEX uq_cod_dispute_resolution_operation
    ON cod_disputes(tenant_id, resolution_operation_id)
    WHERE resolution_operation_id IS NOT NULL;

CREATE INDEX idx_cod_dispute_tenant_status ON cod_disputes(tenant_id, status, created_at DESC);
CREATE INDEX idx_cod_dispute_driver ON cod_disputes(tenant_id, driver_id, created_at DESC);
