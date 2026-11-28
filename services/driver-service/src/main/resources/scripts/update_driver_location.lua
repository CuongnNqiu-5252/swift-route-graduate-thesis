-- ==============================================================================
-- update_driver_location.lua
-- Atomic Lua script to update driver location and heartbeat in Redis
-- KEYS[1]: driver:locations:<tenant_id>
-- KEYS[2]: driver:heartbeat:<driver_id>
-- ARGV[1]: lng (longitude)
-- ARGV[2]: lat (latitude)
-- ARGV[3]: driver_id
-- ARGV[4]: heartbeat_ttl_seconds
-- ==============================================================================

redis.call('GEOADD', KEYS[1], ARGV[1], ARGV[2], ARGV[3])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[4])
return 1

