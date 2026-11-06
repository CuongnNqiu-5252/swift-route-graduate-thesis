-- ==============================================================================
-- sweep_stale_driver.lua
-- Atomic Lua script to remove stale drivers from Redis sorted set
-- KEYS[1]: driver:heartbeat:<driver_id>
-- KEYS[2]: driver:locations:<tenant_id>
-- ARGV[1]: driver_id
-- ==============================================================================

if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('ZREM', KEYS[2], ARGV[1])
    return 1 -- Stale driver removed successfully
else
    return 0 -- Fresh heartbeat exists, abort deletion
end

