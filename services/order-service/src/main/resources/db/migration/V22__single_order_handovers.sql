-- The conversion is lossless only when each order has one physical parcel.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM orders WHERE package_count <> 1)
        OR EXISTS (SELECT 1 FROM package_units GROUP BY order_id HAVING count(*) > 1)
        OR EXISTS (
            SELECT 1 FROM package_handovers h
            LEFT JOIN package_handover_items i USING (handover_id)
            LEFT JOIN package_units u USING (package_id)
            GROUP BY h.handover_id, h.tenant_id
            HAVING count(u.package_id) <> 1 OR bool_or(u.tenant_id <> h.tenant_id)
        )
        OR EXISTS (
            SELECT 1 FROM package_units u JOIN orders o USING (order_id)
            WHERE u.current_custodian_id IS NOT NULL
                AND u.current_custodian_id IS DISTINCT FROM o.assigned_driver_id
        )
        OR EXISTS (SELECT 1 FROM return_requests WHERE package_count <> 1 OR actual_quantity > 1)
    THEN
        RAISE EXCEPTION 'Single-order handover migration requires review of existing parcel, return or assignment data';
    END IF;
END $$;

ALTER TABLE package_handovers ADD COLUMN order_id UUID REFERENCES orders(order_id);
UPDATE package_handovers h
SET order_id = u.order_id
FROM package_handover_items i JOIN package_units u USING (package_id)
WHERE i.handover_id = h.handover_id;
ALTER TABLE package_handovers ALTER COLUMN order_id SET NOT NULL;
CREATE INDEX idx_package_handovers_order ON package_handovers(order_id);
CREATE UNIQUE INDEX ux_active_handover_order ON package_handovers(order_id)
    WHERE status IN ('REQUESTED', 'ASSIGNED');

ALTER TABLE package_custody_events ADD COLUMN order_id UUID REFERENCES orders(order_id);
UPDATE package_custody_events e SET order_id = u.order_id
FROM package_units u WHERE e.package_id = u.package_id;
ALTER TABLE package_custody_events ALTER COLUMN order_id SET NOT NULL;
-- Some legacy units were created without a corresponding custody event.
-- Restore the active custody marker from the stored unit before dropping it.
INSERT INTO package_custody_events (
    custody_event_id, package_id, order_id, tenant_id, event_type,
    to_custodian_id, actor_id, reason, created_at
)
SELECT gen_random_uuid(), u.package_id, u.order_id, u.tenant_id,
    CASE WHEN u.status = 'RETURNING' THEN 'RETURN_STARTED' ELSE 'PICKUP_CONFIRMED' END,
    u.current_custodian_id, u.current_custodian_id, 'Imported from legacy parcel custody',
    clock_timestamp()
FROM package_units u
WHERE u.current_custodian_id IS NOT NULL
    AND u.current_custodian_id IS DISTINCT FROM (
        SELECT e.to_custodian_id FROM package_custody_events e
        WHERE e.order_id = u.order_id ORDER BY e.created_at DESC LIMIT 1
    );
ALTER TABLE package_custody_events DROP COLUMN package_id;
CREATE INDEX idx_custody_events_order ON package_custody_events(order_id, created_at DESC);

DROP TABLE package_handover_items;
DROP TABLE package_units;
ALTER TABLE orders DROP COLUMN package_count, ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE return_requests DROP COLUMN package_count;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_fulfillment_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_fulfillment_status_check CHECK (
    fulfillment_status IN ('DRAFT', 'CONFIRMED', 'READY_FOR_PLANNING', 'PLANNED',
        'IN_TRANSIT', 'DELIVERED', 'FAILED', 'CANCELLED', 'RETURNING', 'RETURNED')
);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (
    status IN ('PENDING_PAYMENT', 'PAYMENT_FAILED', 'PAID', 'PLANNED', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'CANCELLED', 'RETURNED')
);
