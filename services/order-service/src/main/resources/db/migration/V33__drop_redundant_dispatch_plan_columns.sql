-- Drop redundant lifecycle timestamp, approval, acknowledgement, and previously removed entity columns from dispatch_plans.
ALTER TABLE dispatch_plans
    DROP COLUMN IF EXISTS approved_at,
    DROP COLUMN IF EXISTS planned_at,
    DROP COLUMN IF EXISTS requires_approval,
    DROP COLUMN IF EXISTS acknowledged_at,
    DROP COLUMN IF EXISTS ack_deadline_at,
    DROP COLUMN IF EXISTS algorithm_used,
    DROP COLUMN IF EXISTS declined_at,
    DROP COLUMN IF EXISTS decline_reason,
    DROP COLUMN IF EXISTS reassignment_count;
