-- 1. Cap nhat quyen Admin
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@gmail.com';

-- 2. Them 7 Tenants da duoc APPROVED
INSERT INTO tenants (
    tenant_id, business_name, business_type, address, pickup_lat, pickup_lng, 
    optimization_weights, subscription_plan, status, owner_user_id, created_at, status_updated_at, version
) VALUES 
(
    'a1111111-1111-1111-1111-111111111111',
    'Kho Hàng Trung Tâm Quận 1',
    'RETAIL',
    'Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    10.776889,
    106.700806,
    '{"time_weight": 0.5, "cost_weight": 0.5}',
    'PREMIUM',
    'APPROVED',
    (SELECT user_id FROM users WHERE email = 'admin@gmail.com' LIMIT 1),
    now(),
    now(),
    0
),
(
    gen_random_uuid(),
    'Chi Nhánh Quận 10',
    'RESTAURANT',
    'Đường 3 Tháng 2, Quận 10, TP. Hồ Chí Minh',
    10.772500,
    106.669000,
    '{"time_weight": 0.6, "cost_weight": 0.4}',
    'FREE_TRIAL',
    'APPROVED',
    (SELECT user_id FROM users WHERE email = 'admin@gmail.com' LIMIT 1),
    now(),
    now(),
    0
),
(
    gen_random_uuid(),
    'Chuỗi Cafe Sài Gòn Quận 3',
    'CAFE',
    '88 Võ Văn Tần, Phường Võ Thị Sáu, Quận 3, TP. Hồ Chí Minh',
    10.776100,
    106.687200,
    '{"time_weight": 0.7, "cost_weight": 0.3}',
    'PREMIUM',
    'APPROVED',
    (SELECT user_id FROM users WHERE email = 'admin@gmail.com' LIMIT 1),
    now(),
    now(),
    0
),
(
    gen_random_uuid(),
    'Nhà Thuốc Tiện Lợi Quận 5',
    'PHARMACY',
    '235 Nguyễn Trãi, Phường 2, Quận 5, TP. Hồ Chí Minh',
    10.755400,
    106.666800,
    '{"time_weight": 0.8, "cost_weight": 0.2}',
    'BASIC',
    'APPROVED',
    (SELECT user_id FROM users WHERE email = 'admin@gmail.com' LIMIT 1),
    now(),
    now(),
    0
),
(
    gen_random_uuid(),
    'Trạm Tiếp Vận Bình Thạnh',
    'RETAIL',
    '152 Điện Biên Phủ, Phường 25, Bình Thạnh, TP. Hồ Chí Minh',
    10.801200,
    106.711500,
    '{"time_weight": 0.5, "cost_weight": 0.5}',
    'PREMIUM',
    'APPROVED',
    (SELECT user_id FROM users WHERE email = 'admin@gmail.com' LIMIT 1),
    now(),
    now(),
    0
),
(
    gen_random_uuid(),
    'Kho Hàng Nam Sài Gòn Quận 7',
    'OTHER',
    '101 Nguyễn Thị Thập, Tân Phú, Quận 7, TP. Hồ Chí Minh',
    10.734100,
    106.719800,
    '{"time_weight": 0.4, "cost_weight": 0.6}',
    'FREE_TRIAL',
    'APPROVED',
    (SELECT user_id FROM users WHERE email = 'admin@gmail.com' LIMIT 1),
    now(),
    now(),
    0
),
(
    gen_random_uuid(),
    'Bếp Trung Tâm Tân Bình',
    'RESTAURANT',
    '45 Hoàng Hoa Thám, Phường 13, Tân Bình, TP. Hồ Chí Minh',
    10.803800,
    106.652100,
    '{"time_weight": 0.6, "cost_weight": 0.4}',
    'BASIC',
    'APPROVED',
    (SELECT user_id FROM users WHERE email = 'admin@gmail.com' LIMIT 1),
    now(),
    now(),
    0
) ON CONFLICT DO NOTHING;

-- 3. Cap nhat quyen Tai xe
UPDATE users SET role = 'DRIVER' WHERE email = 'driver@gmail.com';

-- 4. Tao ho so tai xe trong bang drivers
INSERT INTO drivers (
    driver_id, tenant_id, user_id, full_name, phone, license_plate, 
    vehicle_type, driver_type, status, approval_status, 
    current_latitude, current_longitude, created_at, updated_at, version
) 
SELECT 
    u.user_id,
    'a1111111-1111-1111-1111-111111111111',
    u.user_id,
    u.full_name,
    u.phone,
    '59-X1 888.88',
    'MOTORBIKE',
    'DEDICATED',
    'AVAILABLE',
    'APPROVED',
    10.776889,
    106.700806,
    now(),
    now(),
    0
FROM users u
WHERE u.email = 'driver@gmail.com'
ON CONFLICT (driver_id) DO NOTHING;
