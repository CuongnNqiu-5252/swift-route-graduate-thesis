# Bảng Ánh Xạ Quyền Sở Hữu Dữ Liệu Microservices (Table Ownership Matrix)

**Ngày ban hành:** 31/10/2026
**Nguyên tắc chỉ đạo:**
1. Mỗi microservice sở hữu toàn quyền đối với schema và database của riêng mình.
2. Không service nào được truy cập trực tiếp database của service khác (kể cả quyền SELECT).
3. Không tạo Foreign Key vật lý xuyên database; mọi liên kết chéo là **Logical Reference (UUID)**.
4. Mọi tài nguyên thuộc tenant đều phải có cột `tenant_id UUID NOT NULL` để đảm bảo cách ly dữ liệu.

---

## 1. Bảng Tổng Hợp Quyền Sở Hữu (Ownership Matrix)

| Bảng | Trách nhiệm dữ liệu | Service chủ quản | Database hiện tại | Database mục tiêu | Loại khóa ngoại liên quan |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `users` | Tài khoản, mật khẩu hash, role hệ thống, thông tin cơ bản | **Auth Service** | `postgres` | `auth_db` | Độc lập |
| `refresh_token` | Quản lý phiên đăng nhập, rotation token, thu hồi phiên | **Auth Service** | `postgres` | `auth_db` | Physical FK -> `users.user_id` |
| `tenant_memberships` | Phân quyền người dùng trong từng đối tác (Owner, Staff, Admin) | **Auth Service** | `postgres` | `auth_db` | Physical FK -> `users.user_id`<br>Logical Ref -> `tenants.tenant_id` |
| `auth_processed_events` | Idempotency chặn duplicate events phía Auth | **Auth Service** | *(Chưa có)* | `auth_db` | Độc lập |
| `tenants` | Thông tin cửa hàng, đối tác, tọa độ depot, cấu hình điều phối | **Order Service** *(tạm thời)* | `postgres` | `order_db` | Logical Ref -> `users.user_id` (`owner_user_id`) |
| `orders` | Thông tin đơn giao hàng, snapshot địa chỉ, toạ độ, trọng lượng | **Order Service** | `postgres` | `order_db` | Physical FK -> `tenants.tenant_id`<br>Logical Ref -> `users.user_id` (`customer_id`)<br>Logical Ref -> `drivers.driver_id` (`assigned_driver_id`)<br>Logical Ref -> `routing_plans.plan_id` (`plan_id`) |
| `order_status_history` | Audit trail lịch sử thay đổi trạng thái đơn hàng | **Order Service** | *(Chưa có)* | `order_db` | Physical FK -> `orders.order_id` |
| `order_outbox_events` | Transactional Outbox phát hành sự kiện đơn hàng | **Order Service** | `postgres` (`outbox_events`) | `order_db` (`outbox_events`) | Độc lập |
| `order_processed_events` | Idempotency chặn duplicate events phía Order | **Order Service** | *(Chưa có)* | `order_db` | Độc lập |
| `drivers` | Hồ sơ tài xế, biển số xe, ca làm việc, trạng thái hoạt động | **Driver Service** | `postgres` | `driver_db` | Logical Ref -> `users.user_id` (`user_id`)<br>Logical Ref -> `tenants.tenant_id` (`tenant_id`) |
| `driver_earning` | Thu nhập, hoa hồng và trạng thái đối soát mỗi đơn giao | **Driver Service** | `postgres` | `driver_db` | Physical FK -> `drivers.driver_id`<br>Logical Ref -> `orders.order_id` (`order_id`)<br>Logical Ref -> `tenants.tenant_id` (`tenant_id`) |
| `driver_rating` | Đánh giá sao và nhận xét từ khách hàng cho tài xế | **Driver Service** | `postgres` | `driver_db` | Physical FK -> `drivers.driver_id`<br>Logical Ref -> `orders.order_id` (`order_id`) |
| `driver_outbox_events` | Transactional Outbox phát hành sự kiện tài xế | **Driver Service** | `postgres` (`outbox_events`) | `driver_db` (`outbox_events`) | Độc lập |
| `driver_processed_events` | Idempotency chặn duplicate events phía Driver | **Driver Service** | *(Chưa có)* | `driver_db` | Độc lập |
| `payments` | Giao dịch thanh toán (VNPAY, MoMo, COD), mã đối soát, số tiền | **Payment Service** | `payment_db` | `payment_db` | Logical Ref -> `orders.order_id` (`order_id`)<br>Logical Ref -> `users.user_id` (`customer_id`)<br>Logical Ref -> `tenants.tenant_id` (`tenant_id`) |
| `payment_webhook_logs` | Nhật ký IPN callback từ cổng thanh toán để đối soát | **Payment Service** | `payment_db` | `payment_db` | Physical FK -> `payments.payment_id` |
| `payment_outbox_events` | Transactional Outbox phát hành sự kiện thanh toán | **Payment Service** | *(Chưa có)* | `payment_db` (`outbox_events`) | Độc lập |
| `payment_processed_events`| Idempotency chặn duplicate events phía Payment | **Payment Service** | *(Chưa có)* | `payment_db` | Độc lập |
| `routing_plans` | Kế hoạch gom chuyến và phân bổ lộ trình cho tài xế | **Routing Engine** | `routing_engine.db` (SQLite) | `routing_db` (Postgres) | Logical Ref -> `tenants.tenant_id` (`tenant_id`) |
| `routing_stops` | Thứ tự các điểm dừng đã tối ưu theo từng tài xế | **Routing Engine** | `routing_engine.db` (trong JSON) | `routing_db` (Postgres) | Physical FK -> `routing_plans.plan_id`<br>Logical Ref -> `orders.order_id` (`stop_id`)<br>Logical Ref -> `drivers.driver_id` (`driver_id`) |
| `routing_processed_events`| Idempotency sự kiện đơn hàng (`order.created/paid`) | **Routing Engine** | `routing_engine.db` (SQLite) | `routing_db` (Postgres) | Độc lập |

---

## 2. Chi Tiết Ranh Giới & Các Điểm Thiết Kế Quan Trọng

### 2.1. Quyền sở hữu `tenants` và `tenant_memberships`
- **Hiện tại:** `tenants` được định nghĩa và quản lý bên trong `order-service` (cùng bảng `orders`), còn `tenant_memberships` nằm trong `auth-service`.
- **Đánh giá kiến trúc:**
  - `tenants` lưu thông tin hoạt động kinh doanh: tọa độ depot (để tính đường TSP), giờ mở cửa, cấu hình thuật toán gom chuyến (`dispatch_config`), gói cước (`subscription_plan`).
  - Do chưa có `Tenant Service` độc lập và theo quy tắc *không tạo thêm service không cần thiết*, việc tiếp tục để `tenants` trong `order_db` là quyết định thực tế và ổn định nhất.
  - `tenant_memberships` trong `auth_db` đóng vai trò là bảng phân quyền truy cập (Authorization Projection). Một người dùng có thể là `OWNER` của Tenant 1, đồng thời là `STAFF` của Tenant 2.
  - Cột `users.tenant_id` được giữ lại làm `active_tenant_id` mặc định để đảm bảo tương thích ngược 100% với JWT claims hiện có của mobile app và web admin.

### 2.2. Phân tách ranh giới Đơn hàng (`orders`) vs Thanh toán (`payments`)
- **Nguyên tắc:** `Payment Service` là Source of Truth (nguồn sự thật duy nhất) cho các giao dịch tiền tệ, số tiền thanh toán thực tế, mã giao dịch cổng (`gateway_txn_id`), mã đối soát (`txn_ref`), và IPN webhooks.
- `Order Service` chỉ lưu trữ:
  - `fulfillment_status`: Trạng thái xử lý vận chuyển (`DRAFT`, `CONFIRMED`, `READY_FOR_PLANNING`, `PLANNED`, `IN_DELIVERY`, `DELIVERED`, `FAILED`, `CANCELLED`).
  - `payment_status`: Trạng thái thanh toán dạng projection (`UNPAID`, `PENDING`, `PAID`, `FAILED`, `COD_PENDING`, `REFUNDED`).
- Khi khách thanh toán qua VNPAY:
  `payment-service` xử lý IPN -> cập nhật `payments.status = SUCCESS` -> phát sự kiện `payment.completed` qua RabbitMQ -> `order-service` lắng nghe và cập nhật `orders.payment_status = PAID`.

### 2.3. Ranh giới Tài xế (`drivers`) vs Routing Engine
- `Driver Service` sở hữu hồ sơ tài xế, phương tiện, tính toán hoa hồng (`driver_earning`) và đánh giá (`driver_rating`).
- `Routing Engine` chỉ đọc thông tin vị trí và trạng thái rảnh qua API REST hoặc cache Redis để giải bài toán định tuyến.
- Khi kế hoạch hoàn tất, `Routing Engine` phát sự kiện `order.batch.dispatched` chứa danh sách cặp `(order_id, driver_id, plan_id)` để `order-service` cập nhật `assigned_driver_id` và `plan_id`.

---

## 3. Ràng Buộc Khóa Ngoại và Tham Chiếu (Constraints Map)

```
[auth_db]
  users (PK: user_id)
    ▲
    ├─── [Physical FK] ─── refresh_token.user_id
    └─── [Physical FK] ─── tenant_memberships.user_id

[order_db]
  tenants (PK: tenant_id)
    ▲                       [auth_db.users]
    │                             ▲ (Logical Ref: customer_id)
    ├─── [Physical FK] ─── orders ┼ (Logical Ref: assigned_driver_id) ──► [driver_db.drivers]
    │                             ▼ (Logical Ref: plan_id) ────────────► [routing_db.routing_plans]
    │                       order_status_history (Physical FK -> orders.order_id)
    └── (Logical Ref: owner_user_id) ──► [auth_db.users]

[driver_db]
  drivers (PK: driver_id)
    ▲ (Logical Ref: user_id)   ──► [auth_db.users]
    ▲ (Logical Ref: tenant_id) ──► [order_db.tenants]
    │
    ├─── [Physical FK] ─── driver_earning (Logical Ref: order_id ──► [order_db.orders])
    └─── [Physical FK] ─── driver_rating  (Logical Ref: order_id ──► [order_db.orders])

[payment_db]
  payments (PK: payment_id)
    ▲ (Logical Ref: order_id)    ──► [order_db.orders]
    ▲ (Logical Ref: customer_id) ──► [auth_db.users]
    ▲ (Logical Ref: tenant_id)   ──► [order_db.tenants]
    │
    └─── [Physical FK] ─── payment_webhook_logs.payment_id

[routing_db]
  routing_plans (PK: plan_id)
    ▲ (Logical Ref: tenant_id) ──► [order_db.tenants]
    │
    └─── [Physical FK] ─── routing_stops (Logical Ref: order_id ──► [order_db.orders])
```

