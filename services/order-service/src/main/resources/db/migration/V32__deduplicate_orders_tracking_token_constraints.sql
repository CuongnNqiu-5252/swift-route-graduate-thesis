-- Deduplicate UNIQUE constraints and drop the redundant plain index on orders.tracking_token.
-- Keep ux_orders_tracking_token as the single canonical UNIQUE constraint (PostgreSQL
-- automatically maintains a unique B-tree index for it).

DO $$
DECLARE
    duplicate_constraint RECORD;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint con
        WHERE con.conrelid = 'orders'::regclass
          AND con.contype = 'u'
          AND con.conname = 'ux_orders_tracking_token'
    ) THEN
        ALTER TABLE orders
            ADD CONSTRAINT ux_orders_tracking_token UNIQUE (tracking_token);
    END IF;

    FOR duplicate_constraint IN
        SELECT con.conname
        FROM pg_constraint con
        WHERE con.conrelid = 'orders'::regclass
          AND con.contype = 'u'
          AND con.conname <> 'ux_orders_tracking_token'
          AND con.conkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = con.conrelid
                AND attname = 'tracking_token'
                AND NOT attisdropped
          )]::smallint[]
    LOOP
        EXECUTE format('ALTER TABLE orders DROP CONSTRAINT IF EXISTS %I', duplicate_constraint.conname);
    END LOOP;
END $$;

DROP INDEX IF EXISTS idx_orders_tracking_token;
