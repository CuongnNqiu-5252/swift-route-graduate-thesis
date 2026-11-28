# Khảo sát Hiện trạng Kiến trúc Cơ sở Dữ liệu SwiftRoute (Phase 1 Audit)

**Ngày khảo sát:** 31/10/2026
**Mục tiêu:** Kiểm toán toàn diện mã nguồn, lược đồ thực tế, cấu hình kết nối, tệp migration và luồng dữ liệu microservices trước khi thực hiện tái cấu trúc cơ sở dữ liệu.

---

## 1. Hiện trạng Hạ tầng Database & Cấu hình Kết nối

Hạ tầng cơ sở dữ liệu hiện tại đang vận hành trên **1 container PostgreSQL duy nhất** (`swiftroute-postgres` phiên bản PostgreSQL 16) được khởi tạo bởi `docker-compose.yml`:

```yaml
# docker-compose.yml
postgres:
  image: postgres:16
  container_name: swiftroute-postgres
  ports:
    - "5433:5432"
  volumes:
    - pgdata:/var/lib/postgresql/data
    - ./scripts/init-databases.sql:/docker-entrypoint-initdb.d/01-init-databases.sql:ro
```

### 1.1. Tệp khởi tạo Database (`scripts/init-databases.sql`)
```sql
SELECT 'CREATE DATABASE payment_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payment_db')\gexec
```
*Chỉ duy nhất `payment_db` được khởi tạo riêng biệt.*

### 1.2. Danh sách Database thực tế trong PostgreSQL Instance
Kiểm tra qua `\l` trên `swiftroute-postgres`:
1. `postgres` (Default database): Đang dùng chung cho **3 microservices**.
2. `payment_db`: Dùng riêng cho **Payment Service**.

### 1.3. Cấu hình kết nối từng Microservice

| Microservice | Database kết nối | URL cấu hình (`application.yml`) | Hibernate `ddl-auto` | Flyway Status |
| :--- | :--- | :--- | :--- | :--- |
| **auth-service** | `postgres` | `jdbc:postgresql://postgres:5432/postgres` | `update` | `enabled: false` (Chưa kích hoạt) |
| **order-service** | `postgres` | `jdbc:postgresql://postgres:5432/postgres` | `update` | `enabled: true`, bảng `flyway_order_history` |
| **driver-service** | `postgres` | `jdbc:postgresql://postgres:5432/postgres` | `update` | `enabled: true`, bảng `flyway_driver_history` |
| **payment-service** | `payment_db` | `jdbc:postgresql://postgres:5432/payment_db` | `update` | `enabled: true`, bảng `flyway_payment_history` |
| **routing-engine** | SQLite file | `services/routing-engine-service/data/routing_engine.db` | Không dùng | Quản lý thủ công qua Python `sqlite3` |
| **notification-service**| *Chưa có* | Project scaffold rỗng, chưa cấu hình DB, chưa chạy trong Docker | N/A | N/A |

---

## 2. Kiểm toán Dữ liệu và Lược đồ Thực tế (Table Schemas & Row Counts)

### 2.1. Database `postgres` (9 bảng - Dùng chung)

| Bảng | Service sở hữu | Số bản ghi | Khóa chính | Ràng buộc khóa ngoại vật lý (FK) |
| :--- | :--- | :--- | :--- | :--- |
| `users` | Auth Service | 7 | `user_id` (UUID) | Không |
| `refresh_token` | Auth Service | 137 | `token_id` (UUID) | FK `user_id` -> `users.user_id` |
| `tenant_memberships` | Auth Service | 0 | `membership_id` (UUID) | FK `user_id` -> `users.user_id` |
| `tenants` | Order Service | 7 | `tenant_id` (UUID) | Không |
| `orders` | Order Service | 18 | `order_id` (UUID) | FK `tenant_id` -> `tenants.tenant_id` |
| `outbox_events` | **Xung đột (Order & Driver)** | 84 | `event_id` (UUID) | Không |
| `drivers` | Driver Service | 3 | `driver_id` (UUID) | Không |
| `driver_earning` | Driver Service | 0 | `driver_earning_id` (UUID) | Không |
| `driver_rating` | Driver Service | 0 | `driver_rating_id` (UUID) | Không |

> [!IMPORTANT]
> **Hiện trạng Khóa ngoại vật lý (Physical Foreign Keys):**
> Trong database `postgres`, **CHỈ CÓ 3 khóa ngoại vật lý**:
> 1. `orders.tenant_id` -> `tenants.tenant_id` (Cùng nội bộ Order Service)
> 2. `refresh_token.user_id` -> `users.user_id` (Cùng nội bộ Auth Service)
> 3. `tenant_memberships.user_id` -> `users.user_id` (Cùng nội bộ Auth Service)
>
> Toàn bộ các tham chiếu liên-service còn lại (`orders.customer_id`, `orders.assigned_driver_id`, `drivers.user_id`, `drivers.tenant_id`, `driver_earning.order_id`, `tenant_memberships.tenant_id`) **đều là Logical References (chỉ lưu UUID)**, hoàn toàn KHÔNG có Foreign Key ràng buộc chéo. Điều này cho phép bóc tách Database một cách độc lập và an toàn mà không làm gãy ràng buộc toàn vẹn cơ sở dữ liệu.

### 2.2. Database `payment_db` (2 bảng - Độc lập)

| Bảng | Service sở hữu | Số bản ghi | Khóa chính | Ràng buộc khóa ngoại vật lý |
| :--- | :--- | :--- | :--- | :--- |
| `payments` | Payment Service | 3 | `payment_id` (UUID) | Không |
| `payment_webhook_logs` | Payment Service | 0 | `log_id` (UUID) | FK `payment_id` -> `payments.payment_id` |

*Ghi chú:* `payments.order_id`, `payments.customer_id`, `payments.tenant_id` đều là logical references.

### 2.3. Database `routing_engine.db` (SQLite - Cục bộ)

| Bảng | Service sở hữu | Số bản ghi | Khóa chính | Cấu trúc lưu trữ |
| :--- | :--- | :--- | :--- | :--- |
| `processed_events` | Routing Engine | 8 | `event_id` (TEXT) | Ghi nhận event_id đã xử lý (idempotency) |
| `pending_orders` | Routing Engine | 8 | `order_id` (TEXT) | Bảng đệm tạm thời cho đơn hàng chờ tối ưu |
| `routing_plans` | Routing Engine | 2 | `plan_id` (TEXT) | Chứa `payload_json` toàn bộ lộ trình gom chuyến |

---

## 3. Các vấn đề cốt lõi đã phát hiện (Identified Issues)

### 3.1. Vi phạm ranh giới Microservice (Monolithic Database Sharing)
- `auth-service`, `order-service` và `driver-service` cùng kết nối trực tiếp vào một database chung tên là `postgres`.
- Mặc dù không có physical FK xuyên service, nhưng việc dùng chung một database connection khiến các service có thể truy cập hoặc bị ảnh hưởng gián tiếp nếu một service làm nghẽn connection pool hoặc lock bảng.

### 3.2. Xung đột cực kỳ nghiêm trọng trên bảng `outbox_events`
- `order-service` định nghĩa entity `OutboxEvent` trỏ vào `@Table(name = "outbox_events")`.
- `driver-service` cũng định nghĩa entity `OutboxEvent` trỏ vào `@Table(name = "outbox_events")` và chạy migration `V3__create_outbox_events_table.sql`.
- Vì 2 service này cùng trỏ vào database `postgres`, chúng đang **chia sẻ chung một bảng vật lý `outbox_events`**. Hiện tại 84 sự kiện trong bảng đều là của `aggregate_type = 'order'`. Nếu `driver-service` phát sinh sự kiện, scheduler của `order-service` có thể đọc nhầm hoặc gây tranh chấp dữ liệu.

### 3.3. Thiếu cơ chế Idempotency phía Consumer trên PostgreSQL
- `routing-engine` có bảng `processed_events` trong SQLite để đảm bảo tính idempotent.
- Tuy nhiên, `order-service` (khi nhận event `order.batch.dispatched` hoặc `payment.completed`), `driver-service`, và `auth-service` (khi nhận `tenant.created`) hiện xử lý sự kiện mà **chưa có bảng `processed_events` bền vững** trong PostgreSQL để chặn duplicate messages ở mức transaction.

### 3.4. Trạng thái Đơn hàng và Thanh toán bị ghép chung (Status Coupling)
- Trong bảng `orders`, cột `status` mang kiểu `VARCHAR(30)` với các giá trị:
  `PENDING_PAYMENT`, `PAYMENT_FAILED`, `PAID`, `PLANNED`, `IN_TRANSIT`, `DELIVERED`, `FAILED`, `CANCELLED`.
- `PENDING_PAYMENT`, `PAYMENT_FAILED`, `PAID` thực chất là **trạng thái thanh toán (Payment Status)**, trong khi `PLANNED`, `IN_TRANSIT`, `DELIVERED` là **trạng thái điều phối/giao hàng (Fulfillment Status)**.
- Khi khách chọn COD hoặc thanh toán thất bại rồi thử lại, cấu trúc hiện tại gây khó khăn cho việc quản lý các kịch bản thực tế (ví dụ: đơn hàng đã sẵn sàng giao nhưng tiền thu sau COD).

### 3.5. Dữ liệu Đơn hàng chỉ lưu dưới dạng Snapshot phẳng (Flat Order Snapshot)
- Bảng `orders` hiện lưu thông tin hàng hoá qua các cột phẳng:
  `package_category`, `item_description`, `notes`, `weight_kg`.
- Chưa có các bảng chi tiết chuẩn (`order_items`, `order_discounts`, `order_status_history`) để hỗ trợ đơn hàng thương mại đa sản phẩm hoặc theo dõi dòng thời gian audit các lần đổi trạng thái.

### 3.6. Dữ liệu Tài xế bị gộp thành các cột phẳng
- Bảng `drivers` hiện gộp cả thông tin xe (`vehicle_type`, `capacity`, `license_plate`), ca làm việc (`shift_start`, `shift_end`), và trạng thái trực tuyến (`is_available`, `current_lat`, `current_lng`).
- Vị trí GPS cập nhật liên tục vào PostgreSQL bảng `drivers` sẽ tạo ra lượng write I/O lớn không cần thiết thay vì lưu cache ngắn hạn trên Redis.

### 3.7. Routing Engine bị cô lập trên SQLite
- `routing-engine` lưu trữ kế hoạch và đơn chờ trên tệp SQLite cục bộ trong container (`routing_engine.db`).
- Nếu container bị tái tạo hoặc triển khai multi-instance, dữ liệu kế hoạch và đơn hàng chờ tối ưu có nguy cơ bị phân mảnh hoặc mất đồng bộ.

### 3.8. Rủi ro từ Hibernate `ddl-auto: update`
- Cả 4 service Spring Boot đều đang bật `spring.jpa.hibernate.ddl-auto: update`.
- Việc này đã từng gây lỗi ở các lần cập nhật trước (Hibernate tự ý chạy `ALTER COLUMN ... SET NOT NULL` trước khi Flyway áp dụng script migration có giá trị mặc định).

