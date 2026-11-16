CREATE TABLE operation_flow_runtime (
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    flow_kind VARCHAR(12) NOT NULL CHECK (flow_kind IN ('import', 'planning')),
    draft JSONB NOT NULL,
    revision INTEGER NOT NULL CHECK (revision > 0),
    PRIMARY KEY (tenant_id, flow_kind)
);
