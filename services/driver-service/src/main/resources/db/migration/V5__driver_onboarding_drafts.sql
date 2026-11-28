-- Keep existing drivers unchanged; new drafts are created explicitly by the application.
ALTER TABLE drivers ALTER COLUMN license_plate DROP NOT NULL;
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS review_note VARCHAR(1000);
ALTER TABLE drivers DROP CONSTRAINT IF EXISTS drivers_vehicle_type_check;
ALTER TABLE drivers ADD CONSTRAINT drivers_vehicle_type_check
    CHECK (vehicle_type IN ('CAR', 'MOTORCYCLE', 'VAN', 'TRUCK', 'MOTORBIKE'));
ALTER TABLE drivers DROP CONSTRAINT IF EXISTS drivers_status_check;
ALTER TABLE drivers ADD CONSTRAINT drivers_status_check
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'OFFLINE', 'INACTIVE', 'AVAILABLE', 'PENDING_APPROVAL'));
