INSERT INTO tenants (
    tenant_id, business_name, business_type, address, pickup_lat, pickup_lng,
    optimization_weights, subscription_plan, status, owner_user_id, created_at, status_updated_at, version
) VALUES (
    'a1111111-1111-1111-1111-111111111111',
    'Kho Hàng Trung Tâm Quận 1',
    'RETAIL',
    'Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    10.776889,
    106.700806,
    '{"time_weight": 0.5, "cost_weight": 0.5}'::jsonb,
    'PREMIUM',
    'APPROVED',
    '00000000-0000-0000-0000-000000000001',
    now(),
    now(),
    0
) ON CONFLICT (tenant_id) DO NOTHING;

