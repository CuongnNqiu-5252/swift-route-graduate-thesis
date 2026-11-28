-- New self-service accounts must prove email ownership before becoming active.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;

ALTER TABLE users ALTER COLUMN status SET DEFAULT 'PENDING_VERIFICATION';

UPDATE users
SET status = 'PENDING_VERIFICATION'
WHERE status = 'ACTIVE'
  AND is_email_verified = FALSE
  AND role != 'SYSTEM_ADMIN';

ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'INACTIVE', 'SUSPENDED'));
