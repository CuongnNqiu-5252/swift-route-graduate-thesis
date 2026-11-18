-- Drop legacy V1 driver_earning columns (replaced in V4) and align CHECK constraints with Java enums.

-- 1. driver_earning: drop orphaned gross_amount, platform_fee, payment_status and unused CANCELLED status
ALTER TABLE driver_earning DROP CONSTRAINT IF EXISTS driver_earning_payment_status_check;
ALTER TABLE driver_earning DROP CONSTRAINT IF EXISTS driver_earning_status_check;

ALTER TABLE driver_earning
    DROP COLUMN IF EXISTS gross_amount,
    DROP COLUMN IF EXISTS platform_fee,
    DROP COLUMN IF EXISTS payment_status;

DELETE FROM driver_earning WHERE status = 'CANCELLED';

ALTER TABLE driver_earning ADD CONSTRAINT driver_earning_status_check CHECK (
    status IN ('PENDING', 'APPROVED', 'PAID')
);

-- 2. driver_settlement: align status constraint with SettlementStatus enum
ALTER TABLE driver_settlement DROP CONSTRAINT IF EXISTS driver_settlement_status_check;

DELETE FROM driver_settlement WHERE status = 'CANCELLED';

ALTER TABLE driver_settlement ADD CONSTRAINT driver_settlement_status_check CHECK (
    status IN ('DRAFT', 'APPROVED', 'PAID')
);

-- 3. drivers: align status and vehicle_type constraints with DriverStatus and VehicleType enums
ALTER TABLE drivers DROP CONSTRAINT IF EXISTS drivers_status_check;
ALTER TABLE drivers DROP CONSTRAINT IF EXISTS drivers_vehicle_type_check;

UPDATE drivers SET status = 'ACTIVE' WHERE status = 'AVAILABLE';
UPDATE drivers SET status = 'INACTIVE' WHERE status = 'OFFLINE';

UPDATE drivers SET vehicle_type = 'MOTORCYCLE' WHERE vehicle_type = 'MOTORBIKE';
UPDATE drivers SET vehicle_type = 'VAN' WHERE vehicle_type = 'TRUCK';

ALTER TABLE drivers ADD CONSTRAINT drivers_status_check CHECK (
    status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'PENDING_APPROVAL')
);

ALTER TABLE drivers ADD CONSTRAINT drivers_vehicle_type_check CHECK (
    vehicle_type IN ('CAR', 'MOTORCYCLE', 'VAN')
);
