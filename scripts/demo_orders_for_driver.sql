-- ==============================================================================
-- SwiftRoute Demo Data: Orders for Driver App Demo (Mobile Driver)
-- Gồm: 4 đơn đã DELIVERED (tính thù lao ca) + 2 đơn PLANNED (để tài xế bấm giao hoặc báo sự cố)
-- ==============================================================================

-- 1. Xoá sạch toàn bộ đơn hàng cũ
DELETE FROM orders;

-- 2. Chèn các đơn gán cho Tài xế 1 (driver@gmail.com - 00000000-0000-0000-0000-000000000003)
INSERT INTO orders (
    order_id, tenant_id, customer_id, pickup_lat, pickup_lng, pickup_address,
    delivery_lat, delivery_lng, delivery_address,
    time_window_start, time_window_end, weight_kg, priority, status,
    package_category, item_description, notes, assigned_driver_id, created_at, updated_at
) VALUES
-- 4 Đơn đã hoàn tất trong ca (DELIVERED)
(
    'b1111111-1111-1111-1111-111111111101',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.034100, 105.787600, 'Bến Ninh Kiều, Đường Hai Bà Trưng, P. Tân An, Q. Ninh Kiều',
    now() - interval '3 hours', now() - interval '1 hours', 1.5, 'NORMAL', 'DELIVERED',
    'PARCEL', 'Hộp quà mỹ phẩm cao cấp', 'Đã giao người nhận trực tiếp',
    '00000000-0000-0000-0000-000000000003', now() - interval '3 hours', now() - interval '1 hours'
),
(
    'b1111111-1111-1111-1111-111111111102',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.029900, 105.770600, 'Đại học Cần Thơ (Khu II), Đường 3/2, P. Xuân Khánh, Q. Ninh Kiều',
    now() - interval '2 hours', now() - interval '30 minutes', 0.8, 'URGENT', 'DELIVERED',
    'DOCUMENT', 'Hồ sơ hợp đồng thương mại', 'Giao lễ tân phòng đào tạo',
    '00000000-0000-0000-0000-000000000003', now() - interval '2 hours', now() - interval '30 minutes'
),
(
    'b1111111-1111-1111-1111-111111111105',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    9.998200,  105.746500, 'Chợ nổi Cái Răng, Phường Lê Bình, Q. Cái Răng, Cần Thơ',
    now() - interval '2 hours', now() - interval '20 minutes', 1.0, 'NORMAL', 'DELIVERED',
    'PARCEL', 'Thực phẩm dinh dưỡng đóng hộp', 'Đã chuyển giao đúng hẹn',
    '00000000-0000-0000-0000-000000000003', now() - interval '2 hours', now() - interval '20 minutes'
),
(
    'b1111111-1111-1111-1111-111111111106',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.042300, 105.768100, 'Lotte Mart Cần Thơ, 84 Mậu Thân, P. An Hòa, Q. Ninh Kiều',
    now() - interval '1 hours', now() - interval '10 minutes', 4.5, 'NORMAL', 'DELIVERED',
    'PARCEL', 'Thùng sách giáo trình đại học', 'Giao thành công cho nhân viên quầy',
    '00000000-0000-0000-0000-000000000003', now() - interval '1 hours', now() - interval '10 minutes'
),

-- 2 Đơn đang chờ giao (PLANNED) để test "Báo sự cố" hoặc "Bắt đầu giao"
(
    'b1111111-1111-1111-1111-111111111103',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.027900, 105.776600, 'Vincom Plaza Xuân Khánh, 209 Đường 30/4, P. Xuân Khánh, Q. Ninh Kiều',
    now(), now() + interval '3 hours', 2.3, 'NORMAL', 'PLANNED',
    'PARCEL', 'Bộ sạc và phụ kiện laptop', 'Giao giờ hành chính (Có thể test Báo sự cố)',
    '00000000-0000-0000-0000-000000000003', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111104',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.044200, 105.782000, 'Vincom Plaza Hùng Vương, 02 Hùng Vương, P. Thới Bình, Q. Ninh Kiều',
    now(), now() + interval '4 hours', 3.1, 'HIGH', 'PLANNED',
    'FRAGILE', 'Bình gốm thủ công trang trí', 'Hàng dễ vỡ, xin nhẹ tay',
    '00000000-0000-0000-0000-000000000003', now(), now()
);

