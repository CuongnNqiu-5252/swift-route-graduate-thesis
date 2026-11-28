UPDATE tenants
SET dispatch_config = dispatch_config || jsonb_build_object(
    'scheduled_dispatch_enabled', COALESCE((dispatch_config->>'scheduled_dispatch_enabled')::boolean, true),
    'scheduled_dispatch_time', COALESCE(dispatch_config->>'scheduled_dispatch_time', '06:00')
)
WHERE dispatch_config IS NOT NULL;
