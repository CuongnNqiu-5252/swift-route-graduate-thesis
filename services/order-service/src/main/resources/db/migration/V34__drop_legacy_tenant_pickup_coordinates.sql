-- Backfill a default ACTIVE pickup_locations row for any legacy tenant that stored
-- pickup_lat/pickup_lng directly on tenants and has no pickup_locations rows yet.
INSERT INTO pickup_locations (
    id,
    tenant_id,
    name,
    address,
    lat,
    lng,
    contact_name,
    contact_phone,
    is_default,
    status,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    t.tenant_id,
    LEFT(COALESCE(NULLIF(BTRIM(t.business_name), ''), 'Default Pickup Location'), 255),
    LEFT(COALESCE(NULLIF(BTRIM(t.address), ''), 'Default Address'), 500),
    t.pickup_lat,
    t.pickup_lng,
    LEFT(NULLIF(BTRIM(t.business_name), ''), 100),
    LEFT(NULLIF(BTRIM(t.contact_phone), ''), 50),
    TRUE,
    'ACTIVE',
    NOW(),
    NOW()
FROM tenants t
WHERE t.pickup_lat IS NOT NULL
  AND t.pickup_lng IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM pickup_locations p WHERE p.tenant_id = t.tenant_id
  );

ALTER TABLE tenants
    DROP COLUMN IF EXISTS pickup_lat,
    DROP COLUMN IF EXISTS pickup_lng;
