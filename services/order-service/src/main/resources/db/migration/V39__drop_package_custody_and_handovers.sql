-- V39: Drop package custody / peer handover tables and fast-track active delivery tasks to OUT_FOR_DELIVERY

DROP TABLE IF EXISTS package_custody_events CASCADE;
DROP TABLE IF EXISTS package_handovers CASCADE;

DROP INDEX IF EXISTS idx_orders_custodian_driver;
ALTER TABLE orders
    DROP COLUMN IF EXISTS custodian_driver_id;

-- Fast-track any active forward delivery tasks that were waiting for warehouse pickup confirmation
UPDATE delivery_tasks
SET status = 'OUT_FOR_DELIVERY',
    updated_at = now()
WHERE task_type = 'DELIVERY'
  AND status IN ('ASSIGNED', 'ACCEPTED', 'ARRIVED', 'PICKED_UP');

-- Synchronize assigned PLANNED orders with active OUT_FOR_DELIVERY tasks to IN_TRANSIT
UPDATE orders o
SET status = 'IN_TRANSIT',
    updated_at = now()
WHERE o.status = 'PLANNED'
  AND o.assigned_driver_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM delivery_tasks t
      WHERE t.order_id = o.order_id
        AND t.task_type = 'DELIVERY'
        AND t.status = 'OUT_FOR_DELIVERY'
  );
