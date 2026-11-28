-- Remove cancelled dispatch plans, restrict plan_status constraint, and drop obsolete cancellation columns.

UPDATE delivery_incidents
SET dispatch_plan_id = NULL
WHERE dispatch_plan_id IN (SELECT plan_id FROM dispatch_plans WHERE plan_status = 'CANCELLED');

UPDATE delivery_tasks
SET dispatch_plan_id = NULL
WHERE dispatch_plan_id IN (SELECT plan_id FROM dispatch_plans WHERE plan_status = 'CANCELLED');

UPDATE orders
SET plan_id = NULL,
    plan_stop_index = NULL
WHERE plan_id IN (SELECT plan_id FROM dispatch_plans WHERE plan_status = 'CANCELLED');

DELETE FROM dispatch_plans
WHERE plan_status = 'CANCELLED';

ALTER TABLE dispatch_plans DROP CONSTRAINT IF EXISTS dispatch_plans_plan_status_check;

ALTER TABLE dispatch_plans ADD CONSTRAINT dispatch_plans_plan_status_check CHECK (
    plan_status IN ('DRAFT', 'APPROVED', 'DISPATCHED', 'COMPLETED')
);

ALTER TABLE dispatch_plans
    DROP COLUMN IF EXISTS cancelled_at,
    DROP COLUMN IF EXISTS cancel_reason;
