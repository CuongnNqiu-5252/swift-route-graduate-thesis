-- ==============================================================================
-- SwiftRoute Full Demo Seed Script
-- Single idempotent script to set up complete state for the Saturday Demo:
-- 1. Demo Users (Admin, Customer, 3 Drivers) with password 'Password123'
-- 2. Approved Central Tenant (Kho Hàng Trung Tâm Quận 1)
-- 3. 3 Active, Available Drivers in 'drivers' table
-- 4. 8 Realistic Orders in 'PAID' status ready for TSP Route Optimization
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 0. CLEANUP OLD SEED / TEST ENTITIES TO GUARANTEE CONSISTENT UUIDS
-- ------------------------------------------------------------------------------
DELETE FROM refresh_token;
DELETE FROM driver_rating;
DELETE FROM driver_earning;
DELETE FROM drivers;
DELETE FROM orders;
DELETE FROM users WHERE email IN ('admin@gmail.com', 'customer@gmail.com', 'driver@gmail.com', 'driver2@gmail.com', 'driver3@gmail.com');

-- ------------------------------------------------------------------------------
-- 1. USERS (auth_service)
-- Pass: Password123 (BCrypt: $2a$10$LuxS4Tym8CH9ZwLDX8fHru7fUWkITTcrUsykZO/dlohMNzMsms9n6)
-- ------------------------------------------------------------------------------
INSERT INTO users (user_id, email, phone, password_hash, full_name, role, tenant_id, status, created_at)
VALUES
(
    '00000000-0000-0000-0000-000000000001',
    'admin@gmail.com',
    '0909000001',
    '$2a$10$LuxS4Tym8CH9ZwLDX8fHru7fUWkITTcrUsykZO/dlohMNzMsms9n6',
    'Quản Trị Viên Hệ Thống',
    'ADMIN',
    'a1111111-1111-1111-1111-111111111111',
    'ACTIVE',
    now()
),
(
    '00000000-0000-0000-0000-000000000002',
    'customer@gmail.com',
    '0909000002',
    '$2a$10$LuxS4Tym8CH9ZwLDX8fHru7fUWkITTcrUsykZO/dlohMNzMsms9n6',
    'Nguyễn Văn Khách',
    'CUSTOMER',
    NULL,
    'ACTIVE',
    now()
),
(
    '00000000-0000-0000-0000-000000000003',
    'driver@gmail.com',
    '0909000003',
    '$2a$10$LuxS4Tym8CH9ZwLDX8fHru7fUWkITTcrUsykZO/dlohMNzMsms9n6',
    'Nguyễn Văn Tài (Tài xế 1)',
    'DRIVER',
    'a1111111-1111-1111-1111-111111111111',
    'ACTIVE',
    now()
),
(
    '00000000-0000-0000-0000-000000000004',
    'driver2@gmail.com',
    '0909000004',
    '$2a$10$LuxS4Tym8CH9ZwLDX8fHru7fUWkITTcrUsykZO/dlohMNzMsms9n6',
    'Trần Thị Bích (Tài xế 2)',
    'DRIVER',
    'a1111111-1111-1111-1111-111111111111',
    'ACTIVE',
    now()
),
(
    '00000000-0000-0000-0000-000000000005',
    'driver3@gmail.com',
    '0909000005',
    '$2a$10$LuxS4Tym8CH9ZwLDX8fHru7fUWkITTcrUsykZO/dlohMNzMsms9n6',
    'Lê Hoàng Cường (Tài xế 3)',
    'DRIVER',
    'a1111111-1111-1111-1111-111111111111',
    'ACTIVE',
    now()
);

-- ------------------------------------------------------------------------------
-- 2. TENANT (order_service)
-- ------------------------------------------------------------------------------
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
    '00000000-0000-0000-0000-000000000001',
    now(),
    now(),
    0
)
ON CONFLICT (tenant_id) DO UPDATE SET
    business_name = EXCLUDED.business_name,
    status = 'APPROVED',
    pickup_lat = EXCLUDED.pickup_lat,
    pickup_lng = EXCLUDED.pickup_lng;

-- ------------------------------------------------------------------------------
-- 3. DRIVERS (driver_service)
-- ------------------------------------------------------------------------------
INSERT INTO drivers (
    driver_id, tenant_id, user_id, full_name, phone, license_plate,
    current_lat, current_lng, vehicle_type, capacity, driver_type,
    status, is_available, created_at, updated_at
) VALUES
(
    '00000000-0000-0000-0000-000000000003',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000003',
    'Nguyễn Văn Tài (Tài xế 1)',
    '0909000003',
    '59-X1 111.11',
    10.776889,
    106.700806,
    'MOTORCYCLE',
    30,
    'DEDICATED',
    'ACTIVE',
    true,
    now(),
    now()
),
(
    '00000000-0000-0000-0000-000000000004',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000004',
    'Trần Thị Bích (Tài xế 2)',
    '0909000004',
    '59-X1 222.22',
    10.776889,
    106.700806,
    'MOTORCYCLE',
    30,
    'DEDICATED',
    'ACTIVE',
    true,
    now(),
    now()
),
(
    '00000000-0000-0000-0000-000000000005',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000005',
    'Lê Hoàng Cường (Tài xế 3)',
    '0909000005',
    '59-X1 333.33',
    10.776889,
    106.700806,
    'MOTORCYCLE',
    30,
    'DEDICATED',
    'ACTIVE',
    true,
    now(),
    now()
);

-- ------------------------------------------------------------------------------
-- 4. 8 DEMO ORDERS IN 'PAID' STATUS (order_service)
-- Located around District 1, District 3, District 5, District 10 (HCMC)
-- ------------------------------------------------------------------------------
INSERT INTO orders (
    order_id, tenant_id, customer_id, pickup_lat, pickup_lng, pickup_address,
    delivery_lat, delivery_lng, delivery_address,
    time_window_start, time_window_end, weight_kg, priority, status,
    package_category, item_description, notes, created_at, updated_at
) VALUES
(
    'b1111111-1111-1111-1111-111111111101',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.781800, 106.699400, '120 Hai Bà Trưng, Phường Đa Kao, Quận 1',
    now(), now() + interval '4 hours', 1.5, 'NORMAL', 'PAID',
    'PARCEL', 'Hộp quà mỹ phẩm cao cấp', 'Gọi trước khi giao', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111102',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.782500, 106.702200, '45 Lê Duẩn, Phường Bến Nghé, Quận 1',
    now(), now() + interval '3 hours', 0.8, 'URGENT', 'PAID',
    'DOCUMENT', 'Hồ sơ hợp đồng thương mại', 'Giao tại quầy lễ tân tầng 3', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111103',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.773500, 106.699000, '88 Nam Kỳ Khởi Nghĩa, Phường Bến Thành, Quận 1',
    now(), now() + interval '5 hours', 2.3, 'NORMAL', 'PAID',
    'PARCEL', 'Bộ sạc và phụ kiện laptop', 'Giao giờ hành chính', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111104',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.767400, 106.687500, '215 Nguyễn Thị Minh Khai, Nguyễn Cư Trinh, Quận 1',
    now(), now() + interval '4 hours', 3.1, 'HIGH', 'PAID',
    'FRAGILE', 'Bình gốm thủ công trang trí', 'Hàng dễ vỡ, xin nhẹ tay', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111105',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.779200, 106.689600, '140 Võ Văn Tần, Phường Võ Thị Sáu, Quận 3',
    now(), now() + interval '5 hours', 1.0, 'NORMAL', 'PAID',
    'PARCEL', 'Thực phẩm dinh dưỡng đóng hộp', 'Giao cho chị Mai', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111106',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.758200, 106.674900, '280 An Dương Vương, Phường 4, Quận 5',
    now(), now() + interval '6 hours', 4.5, 'NORMAL', 'PAID',
    'PARCEL', 'Thùng sách giáo trình đại học', 'Giao tại cổng trường ĐHSP', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111107',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.765100, 106.666200, '350 Ba Tháng Hai, Phường 12, Quận 10',
    now(), now() + interval '4 hours', 2.0, 'NORMAL', 'PAID',
    'PARCEL', 'Giày thể thao nam cao cấp', 'Gọi trước khi tới 15p', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111108',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.792500, 106.706800, '48 Hoàng Sa, Phường Đa Kao, Quận 1',
    now(), now() + interval '5 hours', 1.2, 'NORMAL', 'PAID',
    'FOOD', 'Bánh ngọt và trà hoa cúc', 'Giao tầng trệt toà nhà văn phòng', now(), now()
);
