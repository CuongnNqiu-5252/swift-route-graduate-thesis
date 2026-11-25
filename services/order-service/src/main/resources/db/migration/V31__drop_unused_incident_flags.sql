ALTER TABLE delivery_incidents
    DROP COLUMN IF EXISTS can_continue_delivery,
    DROP COLUMN IF EXISTS needs_immediate_assistance;
