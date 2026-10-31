-- ==============================================================================
-- SwiftRoute Demo Data: Orders for Dispatching Demo (Web Admin)
-- Tất cả các đơn ở trạng thái 'PAID' (Chờ điều phối), chưa gán tài xế
-- ==============================================================================

-- 1. Xoá sạch toàn bộ đơn hàng cũ
DELETE FROM orders;

-- 2. Chèn 8 đơn hàng mới quanh Kho Tổng Ninh Kiều, Cần Thơ
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
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.034100, 105.787600, 'Bến Ninh Kiều, Đường Hai Bà Trưng, P. Tân An, Q. Ninh Kiều',
    now(), now() + interval '4 hours', 1.5, 'NORMAL', 'PAID',
    'PARCEL', 'Hộp quà mỹ phẩm cao cấp', 'Gọi trước khi giao', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111102',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.029900, 105.770600, 'Đại học Cần Thơ (Khu II), Đường 3/2, P. Xuân Khánh, Q. Ninh Kiều',
    now(), now() + interval '3 hours', 0.8, 'URGENT', 'PAID',
    'DOCUMENT', 'Hồ sơ hợp đồng thương mại', 'Giao tại quầy lễ tân tầng 3', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111103',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.027900, 105.776600, 'Vincom Plaza Xuân Khánh, 209 Đường 30/4, P. Xuân Khánh, Q. Ninh Kiều',
    now(), now() + interval '5 hours', 2.3, 'NORMAL', 'PAID',
    'PARCEL', 'Bộ sạc và phụ kiện laptop', 'Giao giờ hành chính', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111104',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.044200, 105.782000, 'Vincom Plaza Hùng Vương, 02 Hùng Vương, P. Thới Bình, Q. Ninh Kiều',
    now(), now() + interval '4 hours', 3.1, 'HIGH', 'PAID',
    'FRAGILE', 'Bình gốm thủ công trang trí', 'Hàng dễ vỡ, xin nhẹ tay', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111105',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    9.998200,  105.746500, 'Chợ nổi Cái Răng, Phường Lê Bình, Q. Cái Răng, Cần Thơ',
    now(), now() + interval '5 hours', 1.0, 'NORMAL', 'PAID',
    'PARCEL', 'Thực phẩm dinh dưỡng đóng hộp', 'Giao cho chị Mai', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111106',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.042300, 105.768100, 'Lotte Mart Cần Thơ, 84 Mậu Thân, P. An Hòa, Q. Ninh Kiều',
    now(), now() + interval '6 hours', 4.5, 'NORMAL', 'PAID',
    'PARCEL', 'Thùng sách giáo trình đại học', 'Giao tại quầy thông tin', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111107',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.036000, 105.783500, 'Bảo tàng Cần Thơ, 01 Đại lộ Hoà Bình, P. Tân An, Q. Ninh Kiều',
    now(), now() + interval '4 hours', 2.0, 'NORMAL', 'PAID',
    'PARCEL', 'Giày thể thao nam cao cấp', 'Gọi trước khi tới 15p', now(), now()
),
(
    'b1111111-1111-1111-1111-111111111108',
    'a1111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000002',
    10.037100, 105.782800, 'Kho Tổng Ninh Kiều, 01 Đại lộ Hoà Bình, Cần Thơ',
    10.035500, 105.779800, 'Công viên Lưu Hữu Phước, Đại lộ Hoà Bình, P. An Lạc, Q. Ninh Kiều',
    now(), now() + interval '5 hours', 1.2, 'NORMAL', 'PAID',
    'FOOD', 'Bánh ngọt và trà hoa cúc', 'Giao cổng chính công viên', now(), now()
);

