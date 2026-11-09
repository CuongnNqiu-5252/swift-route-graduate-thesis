-- V16: Simplify COD Ledger by removing redundant intermediary tables
-- and managing COD state directly on the orders table.

-- 1. Add COD tracking columns directly to orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_status VARCHAR(30) NOT NULL DEFAULT 'NOT_APPLICABLE';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_collected_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_handover_batch_id UUID REFERENCES cod_handover_batches(handover_id);

-- 2. Backfill existing orders
-- Orders with no COD
UPDATE orders
SET cod_status = 'NOT_APPLICABLE'
WHERE cod_amount = 0;

-- Undelivered orders with COD
UPDATE orders
SET cod_status = 'COD_PENDING'
WHERE cod_amount > 0 AND (status <> 'DELIVERED' OR status IS NULL);

-- Delivered orders with COD: check if already in custody or handed over
UPDATE orders
SET cod_status = 'IN_CUSTODY',
    cod_collected_at = COALESCE(updated_at, created_at, now())
WHERE cod_amount > 0 AND status = 'DELIVERED';

-- Sync with previous cod_collections data if any rows existed
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'cod_collections') THEN
        UPDATE orders o
        SET cod_status = CASE
                WHEN c.status = 'HANDED_OVER' OR c.status = 'VERIFIED' THEN 'HANDED_OVER'
                ELSE 'IN_CUSTODY'
            END,
            cod_collected_at = COALESCE(c.occurred_at, c.collected_at, o.updated_at, now())
        FROM cod_collections c
        WHERE c.order_id = o.order_id;

        IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'cod_handover_lines') THEN
            UPDATE orders o
            SET cod_handover_batch_id = l.handover_id
            FROM cod_collections c
            JOIN cod_handover_lines l ON c.collection_id = l.collection_id
            WHERE c.order_id = o.order_id;
        END IF;
    END IF;
END $$;

-- 3. Add constraint and performance indexes to orders
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_cod_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_cod_status_check
    CHECK (cod_status IN ('NOT_APPLICABLE', 'COD_PENDING', 'IN_CUSTODY', 'HANDED_OVER'));

CREATE INDEX IF NOT EXISTS idx_orders_driver_cod_custody
    ON orders(tenant_id, assigned_driver_id, cod_status);

CREATE INDEX IF NOT EXISTS idx_orders_cod_batch
    ON orders(cod_handover_batch_id);

CREATE INDEX IF NOT EXISTS idx_orders_cod_status
    ON orders(tenant_id, cod_status);

-- 4. Drop obsolete tables safely (cod_disputes, cod_handover_lines, cod_collections, payments)
DROP TABLE IF EXISTS cod_disputes CASCADE;
DROP TABLE IF EXISTS cod_handover_lines CASCADE;
DROP TABLE IF EXISTS cod_collections CASCADE;
DROP TABLE IF EXISTS payments CASCADE;

