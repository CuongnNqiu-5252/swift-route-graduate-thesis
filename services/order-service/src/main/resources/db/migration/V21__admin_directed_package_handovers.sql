-- Existing active records were created under the old driver-selected,
-- dual-confirmation workflow. Cancel and unlock them instead of silently
-- treating the selected driver as an administrator-approved assignment.
UPDATE package_units unit
SET status = item.previous_status,
    updated_at = now()
FROM package_handover_items item
JOIN package_handovers handover ON handover.handover_id = item.handover_id
WHERE unit.package_id = item.package_id
  AND item.status = 'PENDING'
  AND handover.status IN ('REQUESTED', 'SENDER_CONFIRMED', 'DISPUTED', 'EXPIRED');

UPDATE package_handover_items item
SET status = 'CANCELLED'
FROM package_handovers handover
WHERE handover.handover_id = item.handover_id
  AND item.status = 'PENDING'
  AND handover.status IN ('REQUESTED', 'SENDER_CONFIRMED', 'DISPUTED', 'EXPIRED');

UPDATE package_handovers
SET status = 'CANCELLED',
    cancelled_at = COALESCE(cancelled_at, now()),
    updated_at = now()
WHERE status IN ('REQUESTED', 'SENDER_CONFIRMED', 'DISPUTED', 'EXPIRED');

ALTER TABLE package_handovers
    ALTER COLUMN to_driver_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS assigned_by UUID,
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_by UUID,
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_by UUID,
    ADD COLUMN IF NOT EXISTS admin_notes VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS receiver_notes VARCHAR(1000);

ALTER TABLE package_handovers
    DROP COLUMN IF EXISTS requested_by;

-- Preserve the original confirmations and notes on historical slips.

ALTER TABLE package_handovers
    DROP CONSTRAINT IF EXISTS package_handovers_status_check,
    DROP CONSTRAINT IF EXISTS package_handovers_check;

ALTER TABLE package_handovers
    ADD CONSTRAINT package_handovers_status_check
    CHECK (status IN ('REQUESTED', 'ASSIGNED', 'COMPLETED', 'REJECTED', 'CANCELLED'));

ALTER TABLE package_handovers
    DROP CONSTRAINT IF EXISTS package_handovers_driver_check;

ALTER TABLE package_handovers
    ADD CONSTRAINT package_handovers_driver_check
    CHECK (to_driver_id IS NULL OR from_driver_id <> to_driver_id);

ALTER TABLE package_handovers
    ADD CONSTRAINT package_handovers_assignment_check
    CHECK (
        (status = 'REQUESTED' AND to_driver_id IS NULL AND assigned_by IS NULL AND assigned_at IS NULL)
        OR (status = 'ASSIGNED' AND to_driver_id IS NOT NULL AND assigned_by IS NOT NULL AND assigned_at IS NOT NULL)
        OR (status = 'COMPLETED' AND to_driver_id IS NOT NULL)
        OR status IN ('REJECTED', 'CANCELLED')
    );
