-- ==============================================================================
-- auth_db: V6__drop_users_tenant_id.sql
-- Drop legacy tenant_id column and its index from users table.
-- All tenant memberships and roles are now managed via tenant_memberships.
-- ==============================================================================

DROP INDEX IF EXISTS idx_users_tenant_id;

ALTER TABLE users DROP COLUMN IF EXISTS tenant_id;

