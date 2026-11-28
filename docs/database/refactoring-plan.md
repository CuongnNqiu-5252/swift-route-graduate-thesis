# Kế Hoạch Tái Cấu Trúc Kiến Trúc Cơ Sở Dữ Liệu SwiftRoute (Refactoring Plan)

**Ngày lập:** 31/10/2026
**Nguyên tắc triển khai:** Không làm gián đoạn hệ thống đang chạy, bảo toàn 100% dữ liệu hiện có, tuân thủ nghiêm ngặt ranh giới Database-per-Service.

---

## 1. Mục Tiêu và Phạm Vi Tái Cấu Trúc

1. **Phân tách vật lý hoàn toàn:** Mỗi service chỉ kết nối và quản lý đúng database độc lập của mình trên cùng PostgreSQL instance (`swiftroute-postgres`):
   - `auth_db`: Dành cho Auth Service.
   - `order_db`: Dành cho Order Service.
   - `driver_db`: Dành cho Driver Service.
   - `payment_db`: Dành cho Payment Service (đã có sẵn, cần hoàn thiện Flyway và Outbox).
   - `routing_db`: Nâng cấp từ SQLite sang PostgreSQL cho Routing Engine.
2. **Khắc phục xung đột `outbox_events`:** Mỗi service có một bảng outbox riêng biệt trong database của mình (`order_db.outbox_events`, `driver_db.outbox_events`).
3. **Thêm cơ chế Idempotency phía Consumer:** Bổ sung bảng `processed_events` trong từng database để đảm bảo xử lý sự kiện RabbitMQ an toàn, chống duplicate.
4. **Chuẩn hóa vòng đời Đơn hàng & Thanh toán:** Tách bạch trường `status` hiện tại thành `fulfillment_status` và `payment_status`, kèm bảng `order_status_history` ghi nhận audit trail.
5. **Chuyển giao an toàn từ SQLite sang PostgreSQL cho Routing Engine:** Tăng cường độ tin cậy, lưu vết lịch sử tối ưu gom chuyến tập trung.

---

## 2. Thiết Kế Lược Đồ Mục Tiêu Chi Tiết (Target Schemas)

### 2.1. `auth_db` (Auth Service)
```sql
-- Flyway V1__init_auth_schema.sql in auth-service
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(30) NOT NULL,
    tenant_id UUID, -- active/default tenant logical reference
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status_created ON users(status, created_at);

CREATE TABLE refresh_token (
    token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    replaced_by_token_id UUID,
    device_info VARCHAR(255),
    ip_address VARCHAR(45)
);
CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_hash ON refresh_token(token_hash);

CREATE TABLE tenant_memberships (
    membership_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL, -- logical ref -> order_db.tenants
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    membership_role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_tenant_memberships_tenant_user UNIQUE (tenant_id, user_id)
);
CREATE INDEX idx_tenant_memberships_user_status ON tenant_memberships(user_id, status);

CREATE TABLE processed_events (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);
```

---

### 2.2. `order_db` (Order Service)
```sql
-- Flyway V1__init_order_schema.sql in order-service
CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL, -- logical ref -> auth_db.users
    business_name VARCHAR(255) NOT NULL,
    business_type VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    address VARCHAR(500),
    pickup_lat DOUBLE PRECISION,
    pickup_lng DOUBLE PRECISION,
    optimization_weights JSONB NOT NULL DEFAULT '{"time_weight":0.5,"cost_weight":0.5}',
    dispatch_config JSONB NOT NULL DEFAULT '{"auto_dispatch_enabled":true,"batch_size_threshold":5,"max_wait_time_seconds":300,"urgent_order_mode":"IMMEDIATE_SINGLE","require_approval":false,"max_orders_per_driver":5,"algorithm":"auto"}',
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'FREE_TRIAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approval_reason VARCHAR(500),
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    status_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_tenants_business_name_normalized ON tenants (LOWER(TRIM(business_name)));
CREATE INDEX idx_tenants_owner_status ON tenants (owner_user_id, status);

CREATE TABLE orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    customer_id UUID NOT NULL, -- logical ref -> auth_db.users
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    pickup_address VARCHAR(500),
    delivery_lat DOUBLE PRECISION NOT NULL,
    delivery_lng DOUBLE PRECISION NOT NULL,
    delivery_address VARCHAR(500),
    time_window_start TIMESTAMPTZ NOT NULL,
    time_window_end TIMESTAMPTZ NOT NULL,
    weight_kg NUMERIC(10,3) NOT NULL CHECK (weight_kg > 0),
    package_category VARCHAR(30) NOT NULL DEFAULT 'PARCEL',
    item_description VARCHAR(255),
    notes VARCHAR(500),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('NORMAL','HIGH','URGENT')),

    -- Trạng thái phân tách chuẩn
    fulfillment_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (fulfillment_status IN ('DRAFT','CONFIRMED','READY_FOR_PLANNING','PLANNED','IN_DELIVERY','DELIVERED','FAILED','CANCELLED')),
    payment_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID'
        CHECK (payment_status IN ('UNPAID','PENDING','PAID','FAILED','COD_PENDING','COLLECTED','REFUNDED')),

    -- Cột legacy để đảm bảo tương thích ngược 100% cho client hiện tại
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',

    assigned_driver_id UUID, -- logical ref -> driver_db.drivers
    plan_id UUID,            -- logical ref -> routing_db.routing_plans
    idempotency_key UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_orders_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX idx_orders_tenant_status ON orders(tenant_id, status);
CREATE INDEX idx_orders_driver_status ON orders(assigned_driver_id, status) WHERE assigned_driver_id IS NOT NULL;
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC);

CREATE TABLE order_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(255),
    changed_by UUID, -- user UUID or system
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_status_history_order ON order_status_history(order_id, created_at ASC);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_order_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);
```

---

### 2.3. `driver_db` (Driver Service)
```sql
-- Flyway V1__init_driver_schema.sql in driver-service
CREATE TABLE drivers (
    driver_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE, -- logical ref -> auth_db.users
    tenant_id UUID NOT NULL, -- logical ref -> order_db.tenants
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL UNIQUE,
    license_plate VARCHAR(20) NOT NULL UNIQUE,
    vehicle_type VARCHAR(30) NOT NULL DEFAULT 'MOTORCYCLE' CHECK (vehicle_type IN ('CAR','MOTORCYCLE','VAN')),
    capacity INTEGER DEFAULT 5,
    shift_start TIME,
    shift_end TIME,
    driver_type VARCHAR(30) NOT NULL DEFAULT 'DEDICATED' CHECK (driver_type IN ('DEDICATED','POOLED')),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','INACTIVE','PENDING_APPROVAL')),
    is_available BOOLEAN NOT NULL DEFAULT FALSE,
    current_lat DOUBLE PRECISION,
    current_lng DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_drivers_tenant_available ON drivers(tenant_id, is_available);
CREATE INDEX idx_drivers_user_id ON drivers(user_id) WHERE user_id IS NOT NULL;

CREATE TABLE driver_earning (
    driver_earning_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    driver_id UUID NOT NULL REFERENCES drivers(driver_id) ON DELETE RESTRICT,
    order_id UUID NOT NULL, -- logical ref -> order_db.orders
    gross_amount NUMERIC(12,2) NOT NULL,
    platform_fee NUMERIC(12,2) NOT NULL,
    net_amount NUMERIC(12,2) NOT NULL,
    payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING','PAID','FAILED','SETTLED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_driver_earning_driver_created ON driver_earning(driver_id, created_at DESC);
CREATE INDEX idx_driver_earning_order ON driver_earning(order_id);

CREATE TABLE driver_rating (
    driver_rating_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(driver_id) ON DELETE CASCADE,
    order_id UUID NOT NULL, -- logical ref -> order_db.orders
    customer_id UUID NOT NULL, -- logical ref -> auth_db.users
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_driver_rating_driver ON driver_rating(driver_id);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_driver_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);
```

---

### 2.4. `routing_db` (Routing Engine - PostgreSQL)
```sql
-- Flyway V1__init_routing_schema.sql in routing-engine
CREATE TABLE routing_plans (
    plan_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL, -- logical ref -> order_db.tenants
    shift VARCHAR(30) NOT NULL DEFAULT 'ALL',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPROVED','DISPATCHED','CANCELLED')),
    algorithm_used VARCHAR(50) NOT NULL,
    total_orders INTEGER NOT NULL DEFAULT 0,
    total_distance_km NUMERIC(8,2) NOT NULL DEFAULT 0.0,
    total_duration_minutes NUMERIC(8,1) NOT NULL DEFAULT 0.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatched_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_routing_plans_tenant_created ON routing_plans(tenant_id, created_at DESC);

CREATE TABLE driver_routes (
    route_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES routing_plans(plan_id) ON DELETE CASCADE,
    driver_id UUID, -- logical ref -> driver_db.drivers
    driver_name VARCHAR(255),
    total_distance_km NUMERIC(8,2) NOT NULL DEFAULT 0.0,
    total_duration_minutes NUMERIC(8,1) NOT NULL DEFAULT 0.0
);
CREATE INDEX idx_driver_routes_plan ON driver_routes(plan_id);

CREATE TABLE route_stops (
    stop_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id UUID NOT NULL REFERENCES driver_routes(route_id) ON DELETE CASCADE,
    order_id UUID NOT NULL, -- logical ref -> order_db.orders
    sequence_index INTEGER NOT NULL,
    location_lat DOUBLE PRECISION NOT NULL,
    location_lng DOUBLE PRECISION NOT NULL,
    location_address VARCHAR(500),
    demand_kg NUMERIC(8,3) DEFAULT 1.0,
    estimated_arrival TIMESTAMPTZ,
    actual_arrival TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
);
CREATE INDEX idx_route_stops_route_seq ON route_stops(route_id, sequence_index ASC);

CREATE TABLE pending_orders (
    order_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID,
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    pickup_address VARCHAR(500),
    delivery_lat DOUBLE PRECISION NOT NULL,
    delivery_lng DOUBLE PRECISION NOT NULL,
    delivery_address VARCHAR(500),
    weight_kg NUMERIC(8,3) NOT NULL DEFAULT 1.0,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    time_window_start TIMESTAMPTZ,
    time_window_end TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL,
    shift VARCHAR(30) NOT NULL DEFAULT 'MORNING',
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pending_orders_tenant_status ON pending_orders(tenant_id, status, received_at ASC);

CREATE TABLE processed_events (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);
```

---

## 3. Trình Tự Thực Thi Di Chuyển An Toàn (Safe Migration Steps)

Trình tự di chuyển tuân thủ nguyên tắc không xoá dữ liệu cũ, kiểm tra checksum từng bước:

```mermaid
flowchart TD
    Step1["Bước 1: Khởi tạo các DB độc lập<br>(auth_db, order_db, driver_db, routing_db)<br>qua init-databases.sql"] --> Step2["Bước 2: Sao chép dữ liệu hiện có từ postgres DB<br>vào từng Database đích tương ứng"]
    Step2 --> Step3["Bước 3: Xác thực tính toàn vẹn<br>(Row Count Checksum & Data Verification)"]
    Step3 --> Step4["Bước 4: Cập nhật cấu hình kết nối microservices<br>(application.yml & docker-compose.yml)"]
    Step4 --> Step5["Bước 5: Khởi động lại từng service độc lập<br>& Kiểm thử hồi quy end-to-end"]
    Step5 --> Step6["Bước 6: Tắt ddl-auto=update<br>chuyển sang validate và giao quyền cho Flyway"]
```

### Bước 1: Khởi tạo các Database đích
- Cập nhật `scripts/init-databases.sql`:
  Tạo `auth_db`, `order_db`, `driver_db`, `payment_db`, `routing_db`.

### Bước 2: Di chuyển dữ liệu hiện có
- Chuyển `users`, `refresh_token`, `tenant_memberships` từ `postgres` sang `auth_db`.
- Chuyển `tenants`, `orders`, `outbox_events` (lọc `aggregate_type = 'order'`) từ `postgres` sang `order_db`.
- Chuyển `drivers`, `driver_earning`, `driver_rating` từ `postgres` sang `driver_db`.
- Chuyển dữ liệu từ SQLite `routing_engine.db` (`pending_orders`, `routing_plans`, `processed_events`) sang `routing_db`.

### Bước 3: Xác thực số lượng bản ghi (Row Count Verification)
- `users`: đúng 7 dòng.
- `refresh_token`: đúng 137 dòng.
- `tenants`: đúng 7 dòng.
- `orders`: đúng 18 dòng.
- `drivers`: đúng 3 dòng.
- `payments`: đúng 3 dòng.

### Bước 4: Chuyển cấu hình kết nối (`docker-compose.yml` & `application.yml`)
- `auth-service` -> `DB_NAME: auth_db`
- `order-service` -> `DB_NAME: order_db`
- `driver-service` -> `DB_NAME: driver_db`
- `payment-service` -> `PAYMENT_DB_NAME: payment_db`
- `routing-engine` -> `ROUTING_DB_URL: postgresql://openpg:openpgpwd@postgres:5432/routing_db`

### Bước 5: Kiểm thử và chuyển đổi hoàn tất
- Sau khi kiểm thử toàn bộ luồng nghiệp vụ trên các database độc lập thành công, cơ sở dữ liệu `postgres` cũ sẽ được lưu lại dưới dạng backup trước khi được dọn dẹp.

