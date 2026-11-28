-- ==============================================================================
-- auth_db: V9__simplify_system_role_and_tenant_admin.sql
-- Normalize users.role to (SYSTEM_ADMIN, USER) and rename tenant role ADMIN -> TENANT_ADMIN
-- ==============================================================================

-- 1. Drop old CHECK first, then normalize users.role values, then apply new CHECK/DEFAULT
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

UPDATE users
SET role = 'SYSTEM_ADMIN'
WHERE role IN ('ADMIN', 'SYSTEM_ADMIN');

UPDATE users
SET role = 'USER'
WHERE role NOT IN ('SYSTEM_ADMIN', 'USER');

ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('SYSTEM_ADMIN', 'USER'));

-- 2. Drop old CHECK first, then normalize tenant_memberships.membership_role, then apply new CHECK
ALTER TABLE tenant_memberships DROP CONSTRAINT IF EXISTS tenant_memberships_membership_role_check;

UPDATE tenant_memberships
SET membership_role = 'TENANT_ADMIN'
WHERE membership_role = 'ADMIN';

UPDATE tenant_memberships
SET membership_role = 'VIEWER'
WHERE membership_role = 'STAFF';

UPDATE tenant_memberships
SET membership_role = 'OPERATOR'
WHERE membership_role = 'MANAGER';

ALTER TABLE tenant_memberships ALTER COLUMN membership_role SET DEFAULT 'OPERATOR';
ALTER TABLE tenant_memberships ADD CONSTRAINT tenant_memberships_membership_role_check
    CHECK (membership_role IN ('OWNER', 'TENANT_ADMIN', 'OPERATOR', 'DRIVER', 'VIEWER'));

-- 3. Drop old CHECK on tenant_invitations.role first, then normalize values, then apply new CHECK
DO $$
DECLARE
    constraint_rec RECORD;
BEGIN
    FOR constraint_rec IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'tenant_invitations'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%role%'
    LOOP
        EXECUTE format('ALTER TABLE tenant_invitations DROP CONSTRAINT %I', constraint_rec.conname);
    END LOOP;
END $$;

UPDATE tenant_invitations
SET role = 'TENANT_ADMIN'
WHERE role = 'ADMIN';

ALTER TABLE tenant_invitations ADD CONSTRAINT tenant_invitations_role_check
    CHECK (role IN ('OWNER', 'TENANT_ADMIN', 'OPERATOR', 'DRIVER', 'VIEWER'));
