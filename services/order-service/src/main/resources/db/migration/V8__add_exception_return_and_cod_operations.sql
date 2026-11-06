CREATE TABLE delivery_incidents (
    incident_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES orders(order_id), tenant_id UUID NOT NULL,
    reported_by UUID NOT NULL, incident_type VARCHAR(60) NOT NULL, notes VARCHAR(1000), status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_by UUID, resolution_notes VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_incidents_tenant_status ON delivery_incidents(tenant_id, status, created_at DESC);
CREATE TABLE return_requests (
    return_request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES orders(order_id), tenant_id UUID NOT NULL,
    request_type VARCHAR(30) NOT NULL CHECK (request_type IN ('REDELIVERY','RETURN_TO_ORIGIN')), reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED', requested_by UUID NOT NULL, approved_by UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_return_requests_tenant_status ON return_requests(tenant_id, status, created_at DESC);
CREATE TABLE cod_collections (
    collection_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES orders(order_id), tenant_id UUID NOT NULL, driver_id UUID NOT NULL,
    collected_amount NUMERIC(12,0) NOT NULL CHECK (collected_amount >= 0), status VARCHAR(30) NOT NULL DEFAULT 'PENDING_HANDOVER', notes VARCHAR(1000),
    declared_amount NUMERIC(12,0), declared_at TIMESTAMPTZ, verified_by UUID, verified_at TIMESTAMPTZ, resolution_notes VARCHAR(1000), collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(order_id)
);
CREATE INDEX idx_cod_collections_tenant_status ON cod_collections(tenant_id, status, collected_at DESC);
