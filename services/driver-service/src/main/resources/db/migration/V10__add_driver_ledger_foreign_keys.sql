-- Add missing physical foreign keys for internal driver_db ledger relations.
-- Uses ON DELETE RESTRICT to protect immutable financial/payroll history from accidental deletes.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_driver_settlement_driver'
          AND conrelid = 'driver_settlement'::regclass
    ) THEN
        ALTER TABLE driver_settlement
            ADD CONSTRAINT fk_driver_settlement_driver
            FOREIGN KEY (driver_id) REFERENCES drivers(driver_id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_driver_earning_driver'
          AND conrelid = 'driver_earning'::regclass
    ) THEN
        ALTER TABLE driver_earning
            ADD CONSTRAINT fk_driver_earning_driver
            FOREIGN KEY (driver_id) REFERENCES drivers(driver_id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_driver_earning_settlement'
          AND conrelid = 'driver_earning'::regclass
    ) THEN
        ALTER TABLE driver_earning
            ADD CONSTRAINT fk_driver_earning_settlement
            FOREIGN KEY (settlement_id) REFERENCES driver_settlement(settlement_id) ON DELETE RESTRICT;
    END IF;
END $$;
