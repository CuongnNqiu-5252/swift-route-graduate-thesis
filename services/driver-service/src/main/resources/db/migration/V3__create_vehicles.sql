ALTER TABLE drivers DROP CONSTRAINT IF EXISTS drivers_vehicle_type_check;
UPDATE drivers SET vehicle_type = CASE
    WHEN vehicle_type IN ('MOTORBIKE', 'MOTORCYCLE') THEN 'MOTORCYCLE'
    WHEN vehicle_type = 'TRUCK' THEN 'VAN'
    ELSE vehicle_type
END;
ALTER TABLE drivers ADD CONSTRAINT drivers_vehicle_type_check
    CHECK (vehicle_type IN ('CAR', 'MOTORCYCLE', 'VAN'));

CREATE TABLE vehicles (
    vehicle_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    license_plate VARCHAR(20) NOT NULL UNIQUE,
    vehicle_type VARCHAR(30) NOT NULL CHECK (vehicle_type IN ('CAR', 'MOTORCYCLE', 'VAN')),
    capacity INTEGER,
    make VARCHAR(80),
    model VARCHAR(80),
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'MAINTENANCE', 'INACTIVE')),
    assigned_driver_id UUID UNIQUE REFERENCES drivers(driver_id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vehicles_tenant_status ON vehicles(tenant_id, status);

INSERT INTO vehicles (tenant_id, license_plate, vehicle_type, capacity, status, assigned_driver_id)
SELECT tenant_id, license_plate, vehicle_type, capacity, 'ASSIGNED', driver_id
FROM drivers
ON CONFLICT (license_plate) DO NOTHING;
