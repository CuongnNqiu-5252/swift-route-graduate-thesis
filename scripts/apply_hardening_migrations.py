import psycopg

print("Applying hardening migrations to PostgreSQL...")

# 1. auth_db
with psycopg.connect("postgresql://openpg:openpgpwd@localhost:5432/auth_db") as conn:
    with conn.cursor() as cur:
        cur.execute("""
            ALTER TABLE tenant_memberships DROP CONSTRAINT IF EXISTS tenant_memberships_status_check;
            ALTER TABLE tenant_memberships ADD CONSTRAINT tenant_memberships_status_check
                CHECK (status IN ('ACTIVE', 'PENDING', 'INACTIVE', 'SUSPENDED', 'REVOKED'));
            ALTER TABLE tenant_memberships ADD COLUMN IF NOT EXISTS last_event_version BIGINT DEFAULT 0;
            ALTER TABLE tenant_memberships ADD COLUMN IF NOT EXISTS last_event_occurred_at TIMESTAMPTZ;
        """)
    conn.commit()
print("  [OK] auth_db tenant_memberships updated.")

# 2. order_db: outbox fields and amount_verification_status
with psycopg.connect("postgresql://openpg:openpgpwd@localhost:5432/order_db") as conn:
    with conn.cursor() as cur:
        cur.execute("""
            ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP;
            ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS dlq_published_at TIMESTAMP;
            ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;
            ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS last_error TEXT;

            ALTER TABLE orders ADD COLUMN IF NOT EXISTS amount_verification_status VARCHAR(30) NOT NULL DEFAULT 'VERIFIED';
            ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_amount_verification_status_check;
            ALTER TABLE orders ADD CONSTRAINT orders_amount_verification_status_check
                CHECK (amount_verification_status IN ('VERIFIED', 'UNVERIFIED_HISTORICAL', 'PENDING_AUDIT'));

            UPDATE orders
            SET amount_verification_status = 'UNVERIFIED_HISTORICAL'
            WHERE notes LIKE '%Legacy order migrated without payment record%'
               OR (total_amount = 0 AND notes LIKE '%Legacy%');
        """)
    conn.commit()
print("  [OK] order_db outbox_events & orders updated.")

# 3. driver_db: outbox fields
with psycopg.connect("postgresql://openpg:openpgpwd@localhost:5432/driver_db") as conn:
    with conn.cursor() as cur:
        cur.execute("""
            ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP;
            ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS dlq_published_at TIMESTAMP;
            ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;
            ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS last_error TEXT;
        """)
    conn.commit()
print("  [OK] driver_db outbox_events updated.")
print("All hardening database migrations applied successfully!")

