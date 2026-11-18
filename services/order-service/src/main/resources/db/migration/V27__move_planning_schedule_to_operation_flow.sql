ALTER TABLE operation_flow_runtime
    ADD COLUMN active_schedule JSONB,
    ADD COLUMN active_revision INTEGER,
    ADD COLUMN next_run_at TIMESTAMPTZ,
    ADD CONSTRAINT operation_flow_active_schedule_check CHECK (
        (active_schedule IS NULL AND active_revision IS NULL AND next_run_at IS NULL)
        OR (flow_kind = 'planning' AND active_schedule IS NOT NULL AND next_run_at IS NOT NULL)
    );

WITH legacy AS (
    SELECT tenant_id,
           CASE WHEN dispatch_config->>'scheduled_dispatch_time' ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'
                THEN dispatch_config->>'scheduled_dispatch_time' ELSE '06:00' END AS run_time,
           (NOT dispatch_config ? 'auto_dispatch_enabled' OR dispatch_config->'auto_dispatch_enabled' = 'true'::jsonb)
               AND (NOT dispatch_config ? 'scheduled_dispatch_enabled'
                   OR dispatch_config->'scheduled_dispatch_enabled' = 'true'::jsonb) AS enabled,
           now() AT TIME ZONE 'Asia/Ho_Chi_Minh' AS local_now
    FROM tenants
    WHERE dispatch_config IS NOT NULL
), schedules AS (
    SELECT tenant_id, enabled,
           jsonb_build_object('mode', 'daily', 'time', run_time, 'hours', 3) AS schedule,
           (local_now::date + run_time::time
               + CASE WHEN run_time::time <= local_now::time THEN interval '1 day' ELSE interval '0' END)
               AT TIME ZONE 'Asia/Ho_Chi_Minh' AS next_run
    FROM legacy
)
INSERT INTO operation_flow_runtime (tenant_id, flow_kind, revision, draft, active_schedule, active_revision, next_run_at)
SELECT tenant_id, 'planning', 1,
       jsonb_build_object(
           'nodes', '[{"id":"schedule","step":"schedule","position":{"x":0,"y":64}},{"id":"plan","step":"plan","position":{"x":244,"y":64}}]'::jsonb,
           'edges', '[{"id":"schedule-plan","source":"schedule","target":"plan"}]'::jsonb,
           'schedule', schedule,
           'resolve_coordinates', true,
           'stop_on_csv_errors', false
       ),
       CASE WHEN enabled THEN schedule END,
       CASE WHEN enabled THEN 1 END,
       CASE WHEN enabled THEN next_run END
FROM schedules
ON CONFLICT (tenant_id, flow_kind) DO UPDATE
    SET active_schedule = EXCLUDED.active_schedule,
        active_revision = CASE
            WHEN operation_flow_runtime.draft->'schedule' = EXCLUDED.active_schedule
                AND jsonb_array_length(operation_flow_runtime.draft->'nodes') = 2
                AND jsonb_array_length(operation_flow_runtime.draft->'edges') = 1
            THEN operation_flow_runtime.revision
        END,
        next_run_at = EXCLUDED.next_run_at;

CREATE INDEX idx_operation_flow_planning_due ON operation_flow_runtime (next_run_at)
    WHERE flow_kind = 'planning' AND next_run_at IS NOT NULL;
