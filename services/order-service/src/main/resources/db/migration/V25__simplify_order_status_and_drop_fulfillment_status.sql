-- Simplify order status lifecycle: replace legacy PAID/payment states with READY_FOR_DISPATCH,
-- promote RETURNING into orders.status, and drop redundant fulfillment_status column.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_fulfillment_status_check;

UPDATE orders
SET status = CASE
    WHEN status IN ('PENDING_PAYMENT', 'PAID') THEN 'READY_FOR_DISPATCH'
    WHEN status = 'PAYMENT_FAILED' THEN 'CANCELLED'
    WHEN status = 'FAILED' AND fulfillment_status = 'RETURNING' THEN 'RETURNING'
    ELSE status
END;

ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'READY_FOR_DISPATCH';

ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (
    status IN (
        'READY_FOR_DISPATCH',
        'PLANNED',
        'IN_TRANSIT',
        'DELIVERED',
        'FAILED',
        'RETURNING',
        'CANCELLED',
        'RETURNED'
    )
);

DROP INDEX IF EXISTS idx_orders_tenant_fulfillment;
ALTER TABLE orders DROP COLUMN IF EXISTS fulfillment_status;
