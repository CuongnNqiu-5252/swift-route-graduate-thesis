-- V1__create_tenants_table.sql
CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name VARCHAR(255) NOT NULL,
    business_type VARCHAR(50) NOT NULL DEFAULT 'OTHER'
        CHECK (business_type IN ('RESTAURANT','CAFE','RETAIL','PHARMACY','OTHER')),
    address VARCHAR(500),
    optimization_weights JSONB NOT NULL DEFAULT '{"time_weight":0.5,"cost_weight":0.5}',
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'FREE_TRIAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','SUSPENDED')),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
