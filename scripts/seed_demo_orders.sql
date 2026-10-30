-- ==============================================================================
-- Seed Demo Orders for SwiftRoute Web Admin & Mobile App
-- 8 Real orders located across District 1, 3, 5, 10 & Bình Thạnh (HCMC)
-- All orders are set to 'PAID' status, ready to be dispatched by Routing Engine (Held-Karp)
-- ==============================================================================

-- Ensure tenant exists
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
    '{"time_weight": 0.5, "cost_weight": 0.5}',
    'PREMIUM',
    'APPROVED',
    '00000000-0000-0000-0000-000000000001',
    now(),
    now(),
    0
) ON CONFLICT (tenant_id) DO NOTHING;

-- Clear previous demo orders with these specific IDs if needed
DELETE FROM orders WHERE order_id IN (
    'b1111111-1111-1111-1111-111111111101',
    'b1111111-1111-1111-1111-111111111102',
    'b1111111-1111-1111-1111-111111111103',
    'b1111111-1111-1111-1111-111111111104',
    'b1111111-1111-1111-1111-111111111105',
    'b1111111-1111-1111-1111-111111111106',
    'b1111111-1111-1111-1111-111111111107',
    'b1111111-1111-1111-1111-111111111108'
);

INSERT INTO orders (
    order_id, tenant_id, customer_id, pickup_lat, pickup_lng, pickup_address,
    delivery_lat, delivery_lng, delivery_address,
    time_window_start, time_window_end, weight_kg, priority, status,
    package_category, item_description, notes, created_at, updated_at
) VALUES
(
    'b1111111-1111-1111-1111-111111111101',
    'a1111111-1111-1111-1111-111111111111',
    'c1111111-1111-1111-1111-111111111111',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.781800, 106.699400, '120 Hai Bà Trưng, Phường Đa Kao, Quận 1',
    now(), now() + interval '4 hours', 1.5, 'NORMAL', 'PAID',
    'PARCEL', 'Hộp quà mỹ phẩm cao cấp', 'Gọi trước khi giao', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111102',
    'a1111111-1111-1111-1111-111111111111',
    'c1111111-1111-1111-1111-111111111111',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.782500, 106.702200, '45 Lê Duẩn, Phường Bến Nghé, Quận 1',
    now(), now() + interval '3 hours', 0.8, 'URGENT', 'PAID',
    'DOCUMENT', 'Hồ sơ hợp đồng thương mại', 'Giao tại quầy lễ tân tầng 3', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111103',
    'a1111111-1111-1111-1111-111111111111',
    'c1111111-1111-1111-1111-111111111111',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.773500, 106.699000, '88 Nam Kỳ Khởi Nghĩa, Phường Bến Thành, Quận 1',
    now(), now() + interval '5 hours', 2.3, 'NORMAL', 'PAID',
    'PARCEL', 'Bộ sạc và phụ kiện laptop', 'Giao giờ hành chính', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111104',
    'a1111111-1111-1111-1111-111111111111',
    'c1111111-1111-1111-1111-111111111111',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.767400, 106.687500, '215 Nguyễn Thị Minh Khai, Nguyễn Cư Trinh, Quận 1',
    now(), now() + interval '4 hours', 3.1, 'HIGH', 'PAID',
    'FRAGILE', 'Bình gốm thủ công trang trí', 'Hàng dễ vỡ, xin nhẹ tay', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111105',
    'a1111111-1111-1111-1111-111111111111',
    'c1111111-1111-1111-1111-111111111111',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.779200, 106.689600, '140 Võ Văn Tần, Phường Võ Thị Sáu, Quận 3',
    now(), now() + interval '5 hours', 1.0, 'NORMAL', 'PAID',
    'PARCEL', 'Thực phẩm dinh dưỡng đóng hộp', 'Giao cho chị Mai', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111106',
    'a1111111-1111-1111-1111-111111111111',
    'c1111111-1111-1111-1111-111111111111',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.758200, 106.674900, '280 An Dương Vương, Phường 4, Quận 5',
    now(), now() + interval '6 hours', 4.5, 'NORMAL', 'PAID',
    'PARCEL', 'Thùng sách giáo trình đại học', 'Giao tại cổng trường ĐHSP', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111107',
    'a1111111-1111-1111-1111-111111111111',
    'c1111111-1111-1111-1111-111111111111',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.801200, 106.711500, '152 Điện Biên Phủ, Phường 25, Bình Thạnh',
    now(), now() + interval '4 hours', 2.0, 'HIGH', 'PAID',
    'PARCEL', 'Tai nghe bluetooth và phụ kiện', 'Gọi số 0901234567', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111108',
    'a1111111-1111-1111-1111-111111111111',
    'c1111111-1111-1111-1111-111111111111',
    10.776889, 106.700806, 'Kho Trung Tâm Q1, Bến Nghé, Quận 1',
    10.772500, 106.669000, 'Đường 3 Tháng 2, Phường 11, Quận 10',
    now(), now() + interval '5 hours', 1.2, 'NORMAL', 'PAID',
    'FOOD', 'Bánh kem sinh nhật đặc biệt', 'Cẩn thận giữ lạnh', now(), now()
);
