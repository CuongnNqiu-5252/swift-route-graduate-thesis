-- V18: Allow 'COMPLETED' in dispatch_plans.plan_status check constraint
ALTER TABLE dispatch_plans DROP CONSTRAINT IF EXISTS dispatch_plans_plan_status_check;
ALTER TABLE dispatch_plans ADD CONSTRAINT dispatch_plans_plan_status_check
    CHECK (plan_status IN ('DRAFT', 'APPROVED', 'DISPATCHED', 'COMPLETED', 'CANCELLED'));
