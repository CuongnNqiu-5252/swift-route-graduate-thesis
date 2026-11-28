-- ==============================================================================
-- order_db: V6__refactor_order_to_delivery_waybill.sql
-- Refactor Order into pure Delivery Waybill (Vận đơn bưu kiện giao nhận)
-- 1. Remove obsolete e-commerce / catalog fields and tables:
--    - Drop table order_items
--    - Drop column customer_id (no mandatory auth user needed for recipients)
--    - Drop column amount_verification_status
-- 2. Add delivery waybill operational fields:
--    - external_order_code, tracking_token, sender_name, sender_phone,
--      recipient_name, recipient_phone, package_description, cod_amount,
--      declared_value, delivery_fee, payer
-- ==============================================================================

-- 1. Drop obsolete order_items table
DROP TABLE IF EXISTS order_items CASCADE;

-- 2. Drop obsolete constraints and indexes on orders
DROP INDEX IF EXISTS idx_orders_customer;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_amount_verification_status_check;

-- 3. Drop obsolete columns from orders
ALTER TABLE orders DROP COLUMN IF EXISTS customer_id CASCADE;
ALTER TABLE orders DROP COLUMN IF EXISTS amount_verification_status CASCADE;

-- 4. Add delivery waybill fields
ALTER TABLE orders ADD COLUMN IF NOT EXISTS external_order_code VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_token VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS sender_name VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS sender_phone VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS package_description VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_amount NUMERIC(12, 0) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS declared_value NUMERIC(12, 0) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_fee NUMERIC(12, 0) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payer VARCHAR(20) NOT NULL DEFAULT 'SENDER';

-- 5. Backfill existing orders with sensible defaults
UPDATE orders
SET tracking_token = UPPER(SUBSTRING(REPLACE(gen_random_uuid()::text, '-', ''), 1, 12))
WHERE tracking_token IS NULL;

UPDATE orders
SET recipient_name = 'Người nhận'
WHERE recipient_name IS NULL;

UPDATE orders
SET recipient_phone = '0900000000'
WHERE recipient_phone IS NULL;

-- 6. Enforce NOT NULL on essential tracking & recipient fields
ALTER TABLE orders ALTER COLUMN tracking_token SET NOT NULL;
ALTER TABLE orders ALTER COLUMN recipient_name SET NOT NULL;
ALTER TABLE orders ALTER COLUMN recipient_phone SET NOT NULL;

-- 7. Add constraints & indexes
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ux_orders_tracking_token;
ALTER TABLE orders ADD CONSTRAINT ux_orders_tracking_token UNIQUE (tracking_token);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_cod_amount_check;
ALTER TABLE orders ADD CONSTRAINT orders_cod_amount_check CHECK (cod_amount >= 0);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_declared_value_check;
ALTER TABLE orders ADD CONSTRAINT orders_declared_value_check CHECK (declared_value >= 0);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_delivery_fee_check;
ALTER TABLE orders ADD CONSTRAINT orders_delivery_fee_check CHECK (delivery_fee >= 0);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payer_check;
ALTER TABLE orders ADD CONSTRAINT orders_payer_check CHECK (payer IN ('SENDER', 'RECIPIENT'));

CREATE INDEX IF NOT EXISTS idx_orders_external_code ON orders(tenant_id, external_order_code);
CREATE INDEX IF NOT EXISTS idx_orders_recipient_phone ON orders(recipient_phone);
CREATE INDEX IF NOT EXISTS idx_orders_tracking_token ON orders(tracking_token);

