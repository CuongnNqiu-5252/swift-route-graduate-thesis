-- Existing accounts retain login behavior. Email verification is required only for new tenant creation.
CREATE TABLE auth_notification_outbox (
    event_id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(user_id),
    email VARCHAR(255) NOT NULL, encrypted_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_notification_pending ON auth_notification_outbox(next_attempt_at) WHERE published_at IS NULL;
-- Backfill explicit legacy membership using the legacy tenant_id as a default only.
DO $migration$
BEGIN
    -- Some installations already migrated ownership entirely to tenant_memberships.
    -- Preserve that schema; only import the legacy default when its column exists.
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'tenant_id'
    ) THEN
        EXECUTE $legacy$
INSERT INTO tenant_memberships(membership_id,tenant_id,user_id,membership_role,status,last_event_version,created_at,updated_at)
SELECT gen_random_uuid(),tenant_id,user_id,
    CASE WHEN role IN ('OWNER','TENANT_OWNER') THEN 'OWNER' WHEN role='DRIVER' THEN 'DRIVER'
         WHEN role IN ('ADMIN','TENANT_ADMIN') THEN 'ADMIN' WHEN role='OPS_STAFF' THEN 'OPERATOR' ELSE 'VIEWER' END,
    'ACTIVE',0,now(),now()
FROM users WHERE tenant_id IS NOT NULL ON CONFLICT (tenant_id,user_id) DO NOTHING;
        $legacy$;
    END IF;
END
$migration$;

UPDATE users SET role='OWNER' WHERE role='TENANT_OWNER';
UPDATE tenant_memberships SET membership_role='VIEWER' WHERE membership_role='STAFF';
UPDATE tenant_memberships SET membership_role='OPERATOR' WHERE membership_role='MANAGER';

-- Approval does not control workspace membership. Preserve explicit suspended/revoked memberships.
UPDATE tenant_memberships SET status='ACTIVE',updated_at=now() WHERE membership_role='OWNER' AND status='PENDING';
