-- V5: Align orders table with the corrected Order entity.
--
-- Changes:
--   1. Add 'pickup_address' column (VARCHAR 500) for the pickup location text.
--   2. Change time_window_start / time_window_end from TIME to TIMESTAMPTZ
--      so that delivery windows carry a full UTC timestamp (date + time + tz),
--      not just a time-of-day without date or timezone.
--
-- Note: Existing rows (if any) will have their time columns migrated by casting
-- TIME → TIMESTAMPTZ using today's date as a placeholder. This is acceptable for
-- a development database. Production data migration must be coordinated separately.

-- Step 1: Add pickup_address if it doesn't already exist
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS pickup_address VARCHAR(500);

-- Step 2: Rename old TIME columns to temporary names so we can recreate them
-- as TIMESTAMPTZ. Renaming is necessary because PostgreSQL does not allow
-- direct ALTER COLUMN ... TYPE TIME → TIMESTAMPTZ without a USING clause.
DO $$
BEGIN
    -- Only run if the column is still TIME type (idempotency guard)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders'
          AND column_name = 'time_window_start'
          AND data_type = 'time without time zone'
    ) THEN
        ALTER TABLE orders
            ADD COLUMN IF NOT EXISTS time_window_start_ts TIMESTAMPTZ,
            ADD COLUMN IF NOT EXISTS time_window_end_ts   TIMESTAMPTZ;

        -- Copy existing values, anchoring to current date in UTC
        UPDATE orders
        SET time_window_start_ts = (CURRENT_DATE + time_window_start) AT TIME ZONE 'UTC',
            time_window_end_ts   = (CURRENT_DATE + time_window_end)   AT TIME ZONE 'UTC'
        WHERE time_window_start IS NOT NULL;

        ALTER TABLE orders DROP COLUMN time_window_start;
        ALTER TABLE orders DROP COLUMN time_window_end;

        ALTER TABLE orders RENAME COLUMN time_window_start_ts TO time_window_start;
        ALTER TABLE orders RENAME COLUMN time_window_end_ts   TO time_window_end;

        -- Restore NOT NULL after data is migrated
        ALTER TABLE orders
            ALTER COLUMN time_window_start SET NOT NULL,
            ALTER COLUMN time_window_end   SET NOT NULL;
    END IF;
END $$;

-- Step 3: Add a CHECK constraint to ensure window end > start
ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS orders_time_window_check;
ALTER TABLE orders
    ADD CONSTRAINT orders_time_window_check
        CHECK (time_window_end > time_window_start);
