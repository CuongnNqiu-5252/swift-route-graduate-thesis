CREATE TABLE order_import_rows (
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    source_code VARCHAR(100) NOT NULL,
    order_id UUID REFERENCES orders(order_id),
    recipient_name VARCHAR(100),
    recipient_phone VARCHAR(50),
    original_address VARCHAR(500),
    window_start TIMESTAMPTZ,
    window_end TIMESTAMPTZ,
    weight_kg DOUBLE PRECISION,
    cod_amount NUMERIC,
    notes VARCHAR(500),
    PRIMARY KEY (tenant_id, source_code)
);

CREATE INDEX idx_order_import_pending ON order_import_rows (tenant_id, source_code) WHERE order_id IS NULL;
