-- Drop redundant/orphaned columns and synchronize status CHECK constraints with runtime code.

-- 1. orders: migrate any legacy item_description and drop item_description & payment_status
UPDATE orders
SET package_description = COALESCE(package_description, item_description)
WHERE package_description IS NULL AND item_description IS NOT NULL;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check;
ALTER TABLE orders
    DROP COLUMN IF EXISTS item_description,
    DROP COLUMN IF EXISTS payment_status;

-- 2. delivery_attempts: preserve photo_url in proof_url and drop duplicate photo_url & unused signature_url
UPDATE delivery_attempts
SET proof_url = COALESCE(proof_url, photo_url)
WHERE proof_url IS NULL AND photo_url IS NOT NULL;

ALTER TABLE delivery_attempts
    DROP COLUMN IF EXISTS photo_url,
    DROP COLUMN IF EXISTS signature_url;

-- 3. package_handovers: preserve legacy notes in admin_notes and drop orphaned V9 columns
UPDATE package_handovers
SET admin_notes = COALESCE(admin_notes, notes)
WHERE admin_notes IS NULL AND notes IS NOT NULL;

ALTER TABLE package_handovers
    DROP COLUMN IF EXISTS notes,
    DROP COLUMN IF EXISTS sender_confirmed_at,
    DROP COLUMN IF EXISTS receiver_confirmed_at;

-- 4. return_requests: add missing EXECUTION_FAILED and drop unused APPROVED & ASSIGNED states
ALTER TABLE return_requests DROP CONSTRAINT IF EXISTS return_requests_status_check;

UPDATE return_requests
SET status = 'IN_EXECUTION'
WHERE status IN ('APPROVED', 'ASSIGNED');

ALTER TABLE return_requests ADD CONSTRAINT return_requests_status_check CHECK (
    status IN (
        'REQUESTED',
        'REJECTED',
        'IN_EXECUTION',
        'ARRIVED',
        'AWAITING_RECEIPT',
        'COMPLETED',
        'DISCREPANCY',
        'EXECUTION_FAILED',
        'CANCELLED'
    )
);

-- 5. cod_handover_batches: restrict status to active post-V20 workflow states
ALTER TABLE cod_handover_batches DROP CONSTRAINT IF EXISTS cod_handover_batches_status_check;

UPDATE cod_handover_batches
SET status = CASE
    WHEN status = 'DRAFT' THEN 'SUBMITTED'
    WHEN status = 'VERIFIED' THEN 'SETTLED'
    WHEN status IN ('DISCREPANCY', 'RESOLVED', 'CANCELLED') THEN 'SETTLED_WITH_VARIANCE'
    ELSE status
END
WHERE status NOT IN ('SUBMITTED', 'SETTLED', 'SETTLED_WITH_VARIANCE');

ALTER TABLE cod_handover_batches ADD CONSTRAINT cod_handover_batches_status_check CHECK (
    status IN ('SUBMITTED', 'SETTLED', 'SETTLED_WITH_VARIANCE')
);

-- 6. delivery_incidents: drop unused CANCELLED status
ALTER TABLE delivery_incidents DROP CONSTRAINT IF EXISTS delivery_incidents_status_check;

UPDATE delivery_incidents
SET status = 'RESOLVED'
WHERE status = 'CANCELLED';

ALTER TABLE delivery_incidents ADD CONSTRAINT delivery_incidents_status_check CHECK (
    status IN ('OPEN', 'IN_PROGRESS', 'ESCALATED', 'RESOLVED')
);

-- 7. package_custody_events: restrict event_type to the 5 implemented custody events
ALTER TABLE package_custody_events DROP CONSTRAINT IF EXISTS package_custody_events_event_type_check;

DELETE FROM package_custody_events
WHERE event_type = 'DISPUTE_OPENED';

ALTER TABLE package_custody_events ADD CONSTRAINT package_custody_events_event_type_check CHECK (
    event_type IN (
        'PICKUP_CONFIRMED',
        'HANDOVER_COMPLETED',
        'DELIVERY_RELEASED',
        'RETURN_STARTED',
        'RETURN_RECEIVED'
    )
);
