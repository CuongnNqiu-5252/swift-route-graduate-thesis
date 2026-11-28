ALTER TABLE orders ADD COLUMN plan_stop_index INTEGER;

-- The order's existing plan_id identifies its current membership. Other join rows are obsolete.
WITH ordered_members AS (
    SELECT o.order_id,
           (ROW_NUMBER() OVER (
               PARTITION BY o.plan_id
               ORDER BY legacy.stop_index NULLS LAST, o.created_at, o.order_id
           ) - 1)::INTEGER AS stop_index
    FROM orders o
    LEFT JOIN dispatch_plan_orders legacy
        ON legacy.order_id = o.order_id AND legacy.plan_id = o.plan_id
    WHERE o.plan_id IS NOT NULL
)
UPDATE orders o
SET plan_stop_index = member.stop_index
FROM ordered_members member
WHERE member.order_id = o.order_id;

ALTER TABLE dispatch_plans
    ADD CONSTRAINT uq_dispatch_plans_tenant_plan UNIQUE (tenant_id, plan_id);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_current_dispatch_plan
        FOREIGN KEY (tenant_id, plan_id) REFERENCES dispatch_plans (tenant_id, plan_id),
    ADD CONSTRAINT chk_orders_plan_stop
        CHECK ((plan_id IS NULL AND plan_stop_index IS NULL)
            OR (plan_id IS NOT NULL AND plan_stop_index IS NOT NULL AND plan_stop_index >= 0)),
    ADD CONSTRAINT uq_orders_plan_stop UNIQUE (plan_id, plan_stop_index) DEFERRABLE INITIALLY DEFERRED;

-- Remove obsolete order IDs from the map snapshot as well as the join table.
WITH snapshots AS (
    SELECT p.plan_id, COALESCE((
        SELECT jsonb_agg(current.point ORDER BY current.plan_stop_index)
        FROM (
            SELECT DISTINCT ON (o.plan_stop_index) o.plan_stop_index, w.point
            FROM jsonb_array_elements(
                CASE WHEN jsonb_typeof(p.route_waypoints) = 'array' THEN p.route_waypoints ELSE '[]'::JSONB END
            ) WITH ORDINALITY AS w(point, position)
            JOIN orders o ON o.plan_id = p.plan_id
                AND o.order_id::TEXT = COALESCE(w.point->>'stop_id', w.point->>'stopId')
            ORDER BY o.plan_stop_index, w.position
        ) current
    ), '[]'::JSONB) AS waypoints
    FROM dispatch_plans p
    WHERE p.route_waypoints IS NOT NULL
)
UPDATE dispatch_plans p
SET route_waypoints = snapshots.waypoints
FROM snapshots
WHERE snapshots.plan_id = p.plan_id AND p.route_waypoints IS DISTINCT FROM snapshots.waypoints;

DROP TABLE dispatch_plan_orders;
