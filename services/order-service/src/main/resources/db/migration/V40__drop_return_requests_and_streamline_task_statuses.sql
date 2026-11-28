-- V40: Drop return_requests table and streamline delivery_tasks to 1-1 forward delivery with 4 statuses

-- 1. Preserve in-flight return intent directly on orders before dropping return_requests
DO $$
BEGIN
    IF to_regclass('public.return_requests') IS NOT NULL THEN
        UPDATE orders o
        SET status = 'RETURNING',
            updated_at = now()
        WHERE o.status = 'FAILED'
          AND EXISTS (
              SELECT 1
              FROM return_requests r
              WHERE r.order_id = o.order_id
                AND r.status IN ('REQUESTED', 'IN_EXECUTION', 'ARRIVED', 'AWAITING_RECEIPT', 'EXECUTION_FAILED', 'DISCREPANCY')
          );
    END IF;
END $$;

DROP TABLE IF EXISTS return_requests CASCADE;

-- 2. Enforce strict 1-1 Order <-> DeliveryTask (re-pointing delivery_attempts before removing extra rows)
WITH ranked_tasks AS (
    SELECT task_id,
           FIRST_VALUE(task_id) OVER (
               PARTITION BY order_id
               ORDER BY CASE WHEN task_type = 'DELIVERY' THEN 0 ELSE 1 END,
                        updated_at DESC,
                        task_id
           ) AS canonical_task_id,
           ROW_NUMBER() OVER (
               PARTITION BY order_id
               ORDER BY CASE WHEN task_type = 'DELIVERY' THEN 0 ELSE 1 END,
                        updated_at DESC,
                        task_id
           ) AS rn
    FROM delivery_tasks
)
UPDATE delivery_attempts a
SET task_id = r.canonical_task_id
FROM ranked_tasks r
WHERE a.task_id = r.task_id
  AND r.rn > 1;

WITH ranked_tasks AS (
    SELECT task_id,
           ROW_NUMBER() OVER (
               PARTITION BY order_id
               ORDER BY CASE WHEN task_type = 'DELIVERY' THEN 0 ELSE 1 END,
                        updated_at DESC,
                        task_id
           ) AS rn
    FROM delivery_tasks
)
DELETE FROM delivery_tasks t
USING ranked_tasks r
WHERE t.task_id = r.task_id
  AND r.rn > 1;

UPDATE delivery_tasks
SET task_type = 'DELIVERY',
    updated_at = now()
WHERE task_type <> 'DELIVERY';

-- 3. Migrate legacy pre-pickup task statuses to OUT_FOR_DELIVERY
UPDATE delivery_tasks
SET status = 'OUT_FOR_DELIVERY',
    updated_at = now()
WHERE status IN ('ASSIGNED', 'ACCEPTED', 'ARRIVED', 'PICKED_UP');

-- 4. Tighten constraints and default on delivery_tasks
ALTER TABLE delivery_tasks
    ALTER COLUMN status SET DEFAULT 'OUT_FOR_DELIVERY';

ALTER TABLE delivery_tasks
    DROP CONSTRAINT IF EXISTS delivery_tasks_task_type_check;
ALTER TABLE delivery_tasks
    ADD CONSTRAINT delivery_tasks_task_type_check
        CHECK (task_type IN ('DELIVERY'));

ALTER TABLE delivery_tasks
    DROP CONSTRAINT IF EXISTS delivery_tasks_status_check;
ALTER TABLE delivery_tasks
    ADD CONSTRAINT delivery_tasks_status_check
        CHECK (status IN ('OUT_FOR_DELIVERY', 'COMPLETED', 'FAILED', 'CANCELLED'));

CREATE UNIQUE INDEX IF NOT EXISTS ux_delivery_tasks_order_id ON delivery_tasks(order_id);
