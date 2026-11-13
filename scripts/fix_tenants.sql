UPDATE tenants
SET dispatch_config = '{"auto_dispatch_enabled": true}'::jsonb
WHERE dispatch_config IS NULL;

