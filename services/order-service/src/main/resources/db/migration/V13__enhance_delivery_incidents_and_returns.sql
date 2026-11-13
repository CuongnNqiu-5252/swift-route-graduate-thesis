-- ==============================================================================
-- order_db: V13__enhance_delivery_incidents_and_returns.sql
-- Production enhancements for Delivery Incident Management & Return-to-Sender (RTS)
-- ==============================================================================

-- 1. Enhance delivery_incidents table
ALTER TABLE delivery_incidents ALTER COLUMN order_id DROP NOT NULL;

ALTER TABLE delivery_incidents
    ADD COLUMN IF NOT EXISTS dispatch_plan_id UUID REFERENCES dispatch_plans(plan_id),
    ADD COLUMN IF NOT EXISTS driver_id UUID,
    ADD COLUMN IF NOT EXISTS severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS evidence_urls TEXT,
    ADD COLUMN IF NOT EXISTS can_continue_delivery BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS needs_immediate_assistance BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS resolution_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS assigned_operator_id UUID,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE delivery_incidents DROP CONSTRAINT IF EXISTS delivery_incidents_status_check;
ALTER TABLE delivery_incidents ADD CONSTRAINT delivery_incidents_status_check CHECK (
    status IN ('OPEN', 'IN_PROGRESS', 'ESCALATED', 'RESOLVED', 'CANCELLED')
);

CREATE INDEX IF NOT EXISTS idx_incidents_plan ON delivery_incidents(dispatch_plan_id);
CREATE INDEX IF NOT EXISTS idx_incidents_driver ON delivery_incidents(driver_id);
CREATE INDEX IF NOT EXISTS idx_incidents_severity ON delivery_incidents(tenant_id, severity, status);

-- 2. Enhance return_requests table
ALTER TABLE return_requests ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE return_requests
    ADD COLUMN IF NOT EXISTS incident_id UUID REFERENCES delivery_incidents(incident_id),
    ADD COLUMN IF NOT EXISTS return_location_id UUID REFERENCES pickup_locations(id),
    ADD COLUMN IF NOT EXISTS current_holder_id UUID,
    ADD COLUMN IF NOT EXISTS package_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS discrepancy_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS actual_quantity INTEGER,
    ADD COLUMN IF NOT EXISTS package_condition VARCHAR(100),
    ADD COLUMN IF NOT EXISTS discrepancy_notes VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS discrepancy_evidence_url VARCHAR(1000);

ALTER TABLE return_requests DROP CONSTRAINT IF EXISTS return_requests_status_check;
ALTER TABLE return_requests ADD CONSTRAINT return_requests_status_check CHECK (
    status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'ASSIGNED', 'IN_EXECUTION', 'ARRIVED', 'AWAITING_RECEIPT', 'COMPLETED', 'DISCREPANCY', 'CANCELLED')
);

ALTER TABLE return_requests DROP CONSTRAINT IF EXISTS return_requests_request_type_check;
ALTER TABLE return_requests ADD CONSTRAINT return_requests_request_type_check CHECK (
    request_type IN ('REDELIVERY', 'RETURN_TO_ORIGIN', 'RETURN_TO_DEPOT')
);

CREATE INDEX IF NOT EXISTS idx_return_requests_incident ON return_requests(incident_id);
CREATE INDEX IF NOT EXISTS idx_return_requests_holder ON return_requests(tenant_id, current_holder_id, status);

-- 3. Enhance delivery_attempts table
ALTER TABLE delivery_attempts
    ADD COLUMN IF NOT EXISTS photo_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS signature_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS lng DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS contact_attempts INTEGER DEFAULT 1;

