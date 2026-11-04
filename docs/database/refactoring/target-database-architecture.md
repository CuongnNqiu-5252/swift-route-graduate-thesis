# Thiết Kế Kiến Trúc Cơ Sở Dữ Liệu Mục Tiêu (Target Database Architecture)

**Giai đoạn:** Phase 2 — Phân Tích & Thiết Kế Kiến Trúc  
**Ngày cập nhật:** 31/10/2026  
**Trạng thái:** HOÀN THIỆN ĐỂ PHÊ DUYỆT CUỐI CÙNG (FINALIZED FOR APPROVAL - REVISION 2)  
**Tác giả:** Đội ngũ Kỹ thuật SwiftRoute  

---

## 1. Nguyên Tắc Thiết Kế Cốt Lõi (Core Principles)

Kiến trúc cơ sở dữ liệu mục tiêu của SwiftRoute được thiết kế nhằm đạt được sự cô lập hoàn toàn giữa các microservice, loại bỏ các điểm nghẽn phụ thuộc, đảm bảo tính nhất quán dữ liệu và sẵn sàng mở rộng quy mô (scalability):

1. **Database-per-Service (Một CSDL Độc Lập Cho Mỗi Dịch Vụ):** Mỗi microservice sở hữu schema và database độc lập trên cụm PostgreSQL 16. Tuyệt đối không có trường hợp hai service đọc/ghi chung một database hoặc bảng dữ liệu nghiệp vụ.
2. **Không Khóa Ngoại Xuyên Database (Zero Cross-DB Foreign Keys):** Mọi mối quan hệ giữa các thực thể thuộc các microservice khác nhau đều là **Logical Reference (UUID)**. Tính toàn vẹn được kiểm soát qua REST API nội bộ hoặc luồng xử lý Event trên RabbitMQ.
3. **Phân Định Trách Nhiệm Rõ Ràng (Bounded Contexts):**
   - `auth_db`: Quản lý danh tính, phiên làm việc, phân quyền thành viên theo cửa hàng.
   - `order_db`: Quản lý thông tin cửa hàng (Depot/Tenant), tiếp nhận đơn hàng, chi tiết hàng hóa (`order_items`), vòng đời giao vận.
   - `driver_db`: Quản lý hồ sơ tài xế, phương tiện, ca làm việc, đối soát thu nhập và đánh giá chất lượng.
   - `payment_db`: Quản lý toàn bộ giao dịch tài chính (cổng thanh toán online VNPAY/MoMo và thu hộ tiền mặt COD).
   - `routing_db`: Quản lý tác vụ tối ưu hóa định tuyến, kế hoạch gom chuyến, thứ tự điểm dừng tối ưu.
   - `notification_db`: Quản lý mẫu thông báo, token thiết bị (FCM), nhật ký gửi tin (khởi tạo baseline schema trong Phase 3).
4. **Cách Ly Đa Khách Thuê Nghiêm Ngặt (Strict Tenant Isolation):** Bảng dữ liệu nghiệp vụ chính thuộc phạm vi cửa hàng bắt buộc chứa cột `tenant_id UUID NOT NULL` và có chỉ mục (index) với tiền tố `tenant_id`. Các bảng con phụ thuộc kế thừa phạm vi tenant thông qua quan hệ khóa ngoại cha.
5. **Đảm Bảo Độ Tin Cậy Bằng Outbox & Idempotency:** Mỗi service có bảng `outbox_events` cục bộ để phát hành sự kiện nguyên tử và bảng `processed_events` để đảm bảo phía nhận không bao giờ bị trùng lặp message.

---

## 2. Ma Trận Quyền Sở Hữu Cơ Sở Dữ Liệu Mục Tiêu (Database Inventory Matrix)

```
Client / Mobile App / Web Admin
           │
     [API Gateway] (Không Database nghiệp vụ - Xác thực JWT & Route)
           │
 ┌─────────┼──────────────┬───────────────┬──────────────┬──────────────────┐
 │         │              │               │              │                  │
[Auth]   [Order]       [Driver]       [Payment]      [Routing Engine]  [Notification]
 │         │              │               │              │                  │
auth_db  order_db      driver_db      payment_db     routing_db        notification_db
 (PG 16)  (PG 16)        (PG 16)        (PG 16)        (PG 16)            (PG 16)
 └─────────┴──────┬───────┴───────────────┴──────────────┴──────────────────┘
                  │
        [RabbitMQ: swiftroute.events] (Async Events)
                  │
          [Redis Geospatial] (Driver Real-time GPS & Heartbeat)
```

Bảng dưới đây thống kê đầy đủ **6 cơ sở dữ liệu mục tiêu** (bao gồm **5 CSDL tạo mới** và **1 CSDL giữ nguyên tại chỗ**):

| Tên Database | Service Chủ Quản | Tình Trạng Triển Khai | Nguồn Dữ Liệu Chuyển Đổi | Các Bảng Trực Thuộc |
| :--- | :--- | :--- | :--- | :--- |
| **`auth_db`** | Auth Service | **Mới (New DB 1/5)** | Di chuyển từ CSDL `postgres` (`users`, `refresh_token`), backfill `tenant_memberships` | `users`, `refresh_token`, `tenant_memberships`, `processed_events` |
| **`order_db`** | Order Service | **Mới (New DB 2/5)** | Di chuyển từ CSDL `postgres` (`tenants`, `orders`, `outbox_events`), khởi tạo `order_items` | `tenants`, `orders`, `order_items`, `order_status_history`, `outbox_events`, `processed_events` |
| **`driver_db`** | Driver Service | **Mới (New DB 3/5)** | Di chuyển từ CSDL `postgres` (`drivers`, `driver_earning`, `driver_rating`) | `drivers`, `driver_earning`, `driver_rating`, `outbox_events`, `processed_events` |
| **`payment_db`** | Payment Service | **Giữ nguyên tại chỗ (Retained In-place)** | Giữ nguyên dữ liệu 3 thanh toán hiện có, chạy migration V2 cập nhật enum COD, Outbox và Idempotency | `payments`, `payment_webhook_logs`, `outbox_events`, `processed_events` |
| **`routing_db`** | Routing Engine | **Mới (New DB 4/5)** | Di chuyển từ file SQLite cục bộ `data/routing_engine.db` sang PostgreSQL | `optimization_jobs`, `routing_plans`, `driver_routes`, `route_stops`, `pending_orders`, `processed_events` |
| **`notification_db`** | Notification Service | **Mới (New DB 5/5 - Baseline Schema)** | Khởi tạo cấu trúc bảng rỗng qua init script Phase 3 để chuẩn bị hạ tầng | `notification_preferences`, `device_tokens`, `notification_deliveries`, `processed_events` |

---

## 3. Chiến Lược Giải Quyết Các Vấn Đề Kỹ Thuật (Architecture Solutions)

### 3.1. Phân định quan hệ Người Dùng & Cửa Hàng (`users` vs `tenant_memberships`)
- **Vấn đề cũ:** Cột `users.tenant_id` lưu trực tiếp 1 tenant ID duy nhất, dẫn đến giả định sai rằng một người dùng chỉ thuộc về một cửa hàng duy nhất và không thể mở rộng mô hình chuỗi cửa hàng.
- **Giải pháp mục tiêu:**
  - `tenant_memberships` (trong `auth_db`) là bảng quan hệ nhiều - nhiều ($N - N$) lưu quyền thành viên (`membership_role`: `OWNER`, `STAFF`, `MANAGER`, `DRIVER`) của người dùng với các Tenant khác nhau.
  - Cột `users.tenant_id` được giữ lại và tái định nghĩa thành **`active_tenant_id`** (Cửa hàng đang được kích hoạt trong phiên làm việc hiện tại).
  - Khi người dùng đăng nhập hoặc chuyển đổi cửa hàng trên Web Admin:
    1. Client gửi yêu cầu chọn cửa hàng `POST /api/auth/switch-tenant/{tenantId}`.
    2. `auth-service` truy vấn bảng `tenant_memberships` để xác thực người dùng có quyền với tenant này hay không (`status = 'ACTIVE'`).
    3. Nếu hợp lệ, hệ thống phát hành Access Token mới có chứa claim `tenant_id` đã được xác thực.
    4. API Gateway đọc claim này để inject header `X-Tenant-Id` cho các service phía sau, ngăn chặn hoàn toàn việc client tự ý giả mạo tenant.

### 3.2. Phân tách vòng đời Đơn hàng, Trạng thái Mặc định An toàn & Tiền tệ VND
- **Trạng thái Mặc định An toàn (Safe Default Order Statuses):**
  - Trong thiết kế cũ, `fulfillment_status` có giá trị mặc định là `'READY_FOR_PLANNING'`, gây rủi ro nghiêm trọng là đơn mới tạo chưa thanh toán đã bị đưa vào hàng đợi gom chuyến định tuyến.
  - Thiết kế mục tiêu sửa đổi giá trị mặc định an toàn tuyệt đối:
    - `orders.status`: `DEFAULT 'PENDING_PAYMENT'`
    - `orders.fulfillment_status`: **`DEFAULT 'DRAFT'`**
    - `orders.payment_status`: `DEFAULT 'PENDING'`
  - **Điều kiện chuyển sang `READY_FOR_PLANNING`:**
    - Đơn thanh toán trực tuyến: Chỉ chuyển khi Payment Service gửi sự kiện `payment.completed` thành công (`payment_status = 'PAID'`).
    - Đơn COD: Chỉ chuyển khi cửa hàng duyệt xác nhận đơn hàng (`order.confirmed` qua Merchant Web Admin).
- **Tương thích ngược (Backward Compatibility):** Vẫn duy trì cột `status` phản ánh trạng thái tổng hợp để Mobile App Flutter (`order_tracking_screen.dart`) và Web Admin hiện tại không bị lỗi hiển thị.
- **Phân định Shipment-level vs Product-level:**
  - *Shipment-level data:* `weight_kg`, `package_category`, `pickup_lat/lng`, `delivery_lat/lng`, `time_window_start/end` phục vụ trực tiếp cho thuật toán VRP của Routing Engine mà không cần join bảng sản phẩm.
  - *Product-level data:* Chi tiết từng món trong bảng con `order_items` phục vụ xuất hóa đơn và đối soát giỏ hàng.
- **Quy chuẩn Tiền tệ VND & Làm tròn (VND Money Precision Policy):**
  - Tiền tệ chuẩn là Việt Nam Đồng (`VND`), không lưu số thập phân trong thương mại thực tế.
  - Sử dụng kiểu `NUMERIC(12, 0)` trong PostgreSQL và `BigDecimal.setScale(0, RoundingMode.HALF_UP)` ở tầng Service.
  - Công thức tính toán:
    $$\text{line\_total} = \text{round}(\text{quantity} \times \text{unit\_price}, 0)$$
    $$\text{orders.total\_amount} = \sum (\text{order\_items.line\_total})$$
  - Backend luôn tính toán lại `total_amount`, đảm bảo `total_amount >= 0`, và truyền số tiền này sang `payment_db.payments.amount`.

### 3.3. Mô hình Tài xế Đơn Khách Thuê & Vai trò Tài xế (`driver_db`)
- Duy trì mô hình **Tài xế đơn khách thuê** (`drivers.tenant_id NOT NULL`). Không triển khai quan hệ đa khách thuê `driver_tenant_contracts` trong Phase này.
- **Định nghĩa các loại tài xế (`driver_type`):**
  - `INTERNAL`: Tài xế chính thức của cửa hàng, sử dụng xe của cửa hàng, nhận lương cố định theo tháng/ca.
  - `CROWDSOURCED`: Tài xế tự do (freelancer) ký hợp đồng nhận ca giao hàng độc quyền cho tenant này trong một khoảng thời gian nhất định.
  - `THIRD_PARTY`: Đơn vị giao nhận thứ 3 (3PL) được tenant ủy thác độc quyền thực hiện tuyến giao.
  - Tất cả các loại tài xế trên đều bị giới hạn nhận đơn trong phạm vi của một `tenant_id` cụ thể.

### 3.4. Vòng Đời Dữ Liệu Vị Trí Realtime của Tài Xế & Xử Lý Race Condition (Redis GPS Lifecycle)
- **Lưu trữ Realtime trên Redis:** Vị trí di chuyển liên tục được cập nhật vào Redis Geospatial theo key:
  `GEOADD driver:locations:<tenant_id> <longitude> <latitude> <driver_id>`
  Các API theo dõi vị trí trực tiếp cho khách hàng và điều phối viên Web Admin truy vấn trực tiếp từ Redis bằng lệnh `GEORADIUS` / `GEOSEARCH` với độ trễ dưới 5ms.
- **Heartbeat & Khắc phục Race Condition (Atomic Lua Script):**
  - Mỗi khi Mobile App tài xế phát vị trí, hệ thống thiết lập một key heartbeat: `driver:heartbeat:<driver_id>` với thời gian sống **TTL = 300 giây (5 phút)**.
  - Để tránh xung đột tranh chấp (Race Condition) giữa tiến trình quét định kỳ dọn tài xế offline và luồng gửi GPS mới từ tài xế, tiến trình dọn dẹp bắt buộc phải sử dụng **Lua Script nguyên tử**:
    ```lua
    -- sweep_stale_driver.lua
    -- KEYS[1]: driver:heartbeat:<driver_id>
    -- KEYS[2]: driver:locations:<tenant_id>
    -- ARGV[1]: driver_id
    if redis.call('EXISTS', KEYS[1]) == 0 then
        redis.call('ZREM', KEYS[2], ARGV[1])
        return 1 -- Đã xóa an toàn vì heartbeat thực sự đã hết hạn
    else
        return 0 -- Heartbeat mới đã đến, hủy bỏ thao tác xóa
    end
    ```
- **Phân định Nguồn sự thật (Source of Truth):**
  - **Redis** là nguồn sự thật cho trạng thái **khả dụng thời gian thực và vị trí di chuyển live** của tài xế (`ONLINE` / `OFFLINE`).
  - **PostgreSQL** (`driver_db.drivers.is_available`) là nguồn sự thật bền vững cho **trạng thái làm việc theo ca** (Tài xế đã bấm nhận ca hay đã chốt ca ra về). Tọa độ trong PostgreSQL chỉ được cập nhật tại các mốc checkpoint (bật/tắt ca, hoàn thành điểm dừng).

### 3.5. Kiến Trúc Đối Soát Thu Hộ Tiền Mặt & Quyền Sở Hữu Tenant (`payment_db`)
- **Quyền sở hữu Tenant nhất quán:** Cột `payments.tenant_id UUID NOT NULL` bắt buộc phải có giá trị và được đối soát trùng khớp với `orders.tenant_id`. Payment Service không chấp nhận giao dịch thanh toán thiếu `tenant_id`.
- **Payment Service là nguồn sự thật tài chính:** Nắm giữ toàn bộ dữ liệu dòng tiền online và tiền mặt COD.
- **Tách biệt rạch ròi 2 sự kiện:**
  - Sự kiện 1 — Giao vận thành công: Tài xế bàn giao kiện hàng cho khách -> Driver Service phát sự kiện `order.delivered` / `driver.delivery_reported.v1`. Sự kiện này **chỉ chuyển trạng thái giao vận** `fulfillment_status = 'DELIVERED'`, trạng thái thanh toán **VẪN LÀ `COD_PENDING`**.
  - Sự kiện 2 — Thu tiền COD thành công: Tài xế kiểm đếm tiền mặt và bấm xác nhận thu tiền trên Mobile App -> Driver Service phát sự kiện chuyên biệt `driver.cod_collected.v1` (kèm `order_id`, `amount_collected`).
- **Chủ thể có thẩm quyền xác nhận thu tiền:**
  1. *Tài xế:* Xác nhận thu tiền mặt từ khách khi giao hàng.
  2. *Thủ quỹ / Ops Staff:* Xác nhận nộp tiền mặt vào két tại kho khi chốt ca trên Web Admin.
- **Xử lý Thu tiền Thiếu / Một phần (Partial Collection):**
  - Nếu `amount_collected < amount`: Bản ghi thanh toán chuyển sang trạng thái `PARTIALLY_COLLECTED`.
  - Hệ thống ghi vết số tiền thiếu `uncollected_amount = amount - amount_collected` và đưa vào danh sách đối soát khiếu nại (Dispute Queue) của Ops Staff.
- **Thời hạn thanh toán (`expires_at`):**
  - Cổng trực tuyến (VNPAY/MoMo): `expires_at NOT NULL` (15 phút).
  - Thanh toán COD: `expires_at NULL` (hoặc đặt hạn chót theo ca giao `time_window_end + INTERVAL '24 hours'`).
- **Đơn hàng đã giao nhưng chưa thu tiền (Delivered but Uncollected):**
  - `orders.fulfillment_status = 'DELIVERED'`, `orders.payment_status = 'COD_PENDING'`.
  - Hiển thị trên màn hình quản lý công nợ shipper. Tài xế không thể kết thúc ca làm việc (Clock-out) nếu chưa bàn giao tiền hoặc giải trình các đơn hàng này.

### 3.6. Tính Toàn Vẹn & Nhất Quán Dữ Liệu Kế Hoạch Định Tuyến (`routing_db`)
- **Authoritative Operational Data vs Immutable Audit Snapshot:**
  - **Dữ liệu vận hành chính thức:** `driver_routes` và `route_stops` là nguồn dữ liệu hoạt động live. Mọi thao tác cập nhật tiến độ (`ARRIVED`, `COMPLETED`, `FAILED`) thực thi trực tiếp trên các dòng của `route_stops`.
  - **Bản lưu trữ bất biến:** Cột `routing_plans.payload_json` lưu trữ nguyên vẹn cấu trúc JSON kết quả đầu ra của thuật toán tại thời điểm giải xong bài toán phục vụ kiểm toán, nghiên cứu luận văn và tái mô phỏng.
- **Các ràng buộc toàn vẹn bắt buộc (Integrity Constraints):**
  - Thứ tự điểm dừng trong một lộ trình là duy nhất: `CONSTRAINT uq_route_stop_index UNIQUE (route_id, stop_index)`.
  - Một đơn hàng không được gán trùng lặp trong cùng một kế hoạch gom chuyến: `CREATE UNIQUE INDEX idx_route_stops_plan_order ON route_stops(route_id, order_id)`.
  - Tính nhất quán số lượng: `routing_plans.total_orders` phải bằng đúng số lượng đơn hàng phân biệt (`DISTINCT order_id`) thực tế có trong kế hoạch.
  - Kiểm tra đồng nhất Tenant: Tài xế gán vào tuyến (`driver_routes.driver_id`) phải có `tenant_id` trùng khớp với `routing_plans.tenant_id`.
  - Máy trạng thái hợp lệ:
    - `routing_plans`: `DRAFT` -> `APPROVED` -> `DISPATCHED` -> `COMPLETED` (hoặc `CANCELLED`).
    - `driver_routes`: `ASSIGNED` -> `IN_PROGRESS` -> `COMPLETED` (hoặc `FAILED`).
    - `route_stops`: `PENDING` -> `ARRIVED` -> `COMPLETED` (hoặc `FAILED`).

### 3.7. Chiến Lược Thực Thi Cô Lập Đa Khách Thuê (Tenant Isolation Enforcement)
- **Quy tắc thiết kế:** Các bảng con phụ thuộc (`order_items`, `driver_routes`, `route_stops`) **không lặp lại cột `tenant_id` (No denormalization)** để tránh dư thừa và bất nhất dữ liệu.
- **Nguyên tắc thực thi an ninh:**
  - **Khóa ngoại hay ORM không tự động bảo vệ tenant:** Khóa ngoại vật lý chỉ đảm bảo tính toàn vẹn tham chiếu nội bộ, không có chức năng ngăn chặn truy cập trái phép chéo tenant.
  - **Trách nhiệm của tầng Service (Application-level Scoping):** Mọi thao tác đọc/ghi bảng con bắt buộc phải thực hiện truy vấn kết hợp (JOIN) với bảng cha có điều kiện `WHERE tenant_id = :tenantId`.
  - **Kiểm thử chéo Tenant (Cross-tenant Integration Tests):** Bắt buộc có các bài test tự động xác nhận Tenant A không thể đọc/sửa dữ liệu của Tenant B ngay cả khi biết chính xác UUID của bản ghi con.
  - **Tác vụ ngầm (Background Jobs & Consumers):** Các job quét batch và message consumer bắt buộc phải trích xuất và gán ngữ cảnh `tenant_id` khi thao tác với dữ liệu.
  - **Định hướng Defense-in-depth:** PostgreSQL Row-Level Security (RLS) là giải pháp tăng cường an ninh được khuyến nghị triển khai ở các giai đoạn sau, nhưng ở hiện tại quyền cách ly được thực thi tại Service Layer.

### 3.8. Cơ Chế Outbox Pattern & Độ Tin Cậy Messaging (RabbitMQ Reliability)
- **Danh sách Service xuất bản Outbox:** `order-service`, `driver-service`, và `payment-service` sở hữu bảng `outbox_events` độc lập.
- **Danh sách Service xử lý Idempotency:** Toàn bộ 6 CSDL đều có bảng `processed_events (event_id, consumer_name, processed_at)`.
- **Tránh xung đột đa tiến trình xuất bản (Concurrent Outbox Publishing):**
  - Tiến trình Outbox Publisher sử dụng câu lệnh khóa dòng không chờ:
    ```sql
    SELECT * FROM outbox_events 
    WHERE published_at IS NULL 
    ORDER BY created_at ASC 
    LIMIT 50 
    FOR UPDATE SKIP LOCKED;
    ```
  - Cơ chế này cho phép nhiều instance của cùng một service chạy song song mà không bao giờ tranh chấp hay xuất bản trùng một sự kiện.
- **Làm rõ Publisher Confirms & At-Least-Once Delivery:**
  - Lệnh `confirm.select` chỉ kích hoạt chế độ Publisher Confirms trên AMQP channel.
  - Service chỉ cập nhật `published_at = now()` khi nhận được tín hiệu xác nhận bất đồng bộ `BasicAck` cho message tương ứng từ RabbitMQ.
  - Trường hợp RabbitMQ đã nhận nhưng service bị sập trước khi cập nhật `published_at`: Sự kiện sẽ được gửi lại khi service phục hồi. Do đó, hệ thống cam kết mức độ đảm bảo **At-Least-Once Delivery** (không ngộ nhận là exactly-once tại tầng vận chuyển mạng).
- **Ranh giới giao dịch nguyên tử phía Consumer (Atomic Idempotent Consumption):**
  - Thao tác ghi nhận `INSERT INTO processed_events` và câu lệnh cập nhật nghiệp vụ được thực thi trong **cùng một database transaction nội bộ**.
  - Nếu xảy ra lỗi giữa chừng, toàn bộ transaction rollback, RabbitMQ NACK message để gửi lại sau.
- **Xử lý thử lại & Dead-Letter Queue (DLQ):**
  - Tối đa 5 lần thử lại với Exponential Backoff ($1s, 2s, 4s, 8s, 16s$).
  - Vượt quá 5 lần: Đánh dấu `status = 'FAILED'`, đẩy payload vào hàng đợi Dead-Letter Queue `swiftroute.events.dlq` và phát cảnh báo tới đội ngũ vận hành.
- **Chính sách dọn dẹp dữ liệu (Retention & Pruning):**
  - Bản ghi Outbox đã xuất bản: Xóa sau 7 ngày (`published_at < now() - INTERVAL '7 days'`).
  - Bản ghi `processed_events`: Xóa sau 30 ngày (`processed_at < now() - INTERVAL '30 days'`).

---

## 4. Chi Tiết Thực Thể và Bảng Tham Chiếu Logic (Cross-Service Logical References)

Trong kiến trúc Microservices mục tiêu, không có bất kỳ foreign key vật lý nào xuyên giữa các database. Bảng dưới đây quy định rõ các trường ID tham chiếu logic:

| Service / Database Nguồn | Bảng | Cột Tham Chiếu Logic | Database & Bảng Đích | Mục Đích Nghiệp Vụ & Cơ Chế Đồng Bộ |
| :--- | :--- | :--- | :--- | :--- |
| **`auth_db`** | `tenant_memberships` | `tenant_id` | `order_db.tenants(tenant_id)` | Lưu quyền thành viên của user đối với tenant. Cập nhật khi nhận event `tenant.created` / `tenant.approved`. |
| **`auth_db`** | `users` | `tenant_id` | `order_db.tenants(tenant_id)` | Tái định nghĩa thành `active_tenant_id` (cửa hàng người dùng đang thao tác). |
| **`order_db`** | `orders` | `customer_id` | `auth_db.users(user_id)` | Định danh khách hàng đặt hàng. Xác thực khi tạo đơn qua JWT claim `sub`. |
| **`order_db`** | `order_items` | `order_id` | `order_db.orders(order_id)` | **Khóa ngoại vật lý nội bộ** trong `order_db` (ON DELETE CASCADE). Không vi phạm ranh giới microservice. |
| **`order_db`** | `order_items` | `product_id` | `product_service.products(product_id)` | **Tham chiếu logic** định danh sản phẩm. KHÔNG tạo khóa ngoại vật lý xuyên CSDL. |
| **`order_db`** | `orders` | `assigned_driver_id` | `driver_db.drivers(driver_id)` | Tài xế được phân công giao đơn. Nhận từ `order.batch.dispatched` hoặc REST call. |
| **`order_db`** | `orders` | `plan_id` | `routing_db.routing_plans(plan_id)` | Kế hoạch định tuyến mà đơn hàng thuộc về. Nhận từ Routing Engine khi chốt lộ trình. |
| **`order_db`** | `tenants` | `owner_user_id` | `auth_db.users(user_id)` | Tài khoản chủ cửa hàng. Xác thực qua Auth Service khi đăng ký tenant. |
| **`driver_db`** | `drivers` | `user_id` | `auth_db.users(user_id)` | Tài khoản đăng nhập của tài xế để truy cập ứng dụng Mobile. |
| **`driver_db`** | `drivers` | `tenant_id` | `order_db.tenants(tenant_id)` | Cửa hàng mà tài xế trực thuộc (Single-Tenant). |
| **`driver_db`** | `driver_earning` | `order_id` | `order_db.orders(order_id)` | Đơn hàng đã hoàn thành làm căn cứ tính cước tài xế. Nhận qua event `driver.delivery_reported.v1`. |
| **`driver_db`** | `driver_rating` | `order_id` | `order_db.orders(order_id)` | Đơn hàng được khách hàng đánh giá sau khi giao thành công. |
| **`driver_db`** | `driver_rating` | `customer_id` | `auth_db.users(user_id)` | Khách hàng thực hiện chấm sao / nhận xét. |
| **`payment_db`** | `payments` | `order_id` | `order_db.orders(order_id)` | Đơn hàng cần thanh toán hoặc đối soát COD. |
| **`payment_db`** | `payments` | `customer_id` | `auth_db.users(user_id)` | Khách hàng thực hiện giao dịch thanh toán. |
| **`payment_db`** | `payments` | `tenant_id` | `order_db.tenants(tenant_id)` | Cửa hàng thụ hưởng thanh toán (Bắt buộc NOT NULL và trùng với `orders.tenant_id`). |
| **`routing_db`** | `pending_orders` | `order_id` | `order_db.orders(order_id)` | Snapshot đơn hàng chờ tối ưu định tuyến. Nhận qua event `order.status_changed.v1` (PAID). |
| **`routing_db`** | `driver_routes` | `driver_id` | `driver_db.drivers(driver_id)` | Tài xế được gắn vào một tuyến đường trong kế hoạch gom chuyến. |
| **`routing_db`** | `route_stops` | `order_id` | `order_db.orders(order_id)` | Điểm dừng tương ứng với một đơn hàng giao cho khách. |
| **`notification_db`**| `notification_preferences` | `user_id` | `auth_db.users(user_id)` | Người dùng cài đặt nhận thông báo. |
| **`notification_db`**| `device_tokens` | `user_id` | `auth_db.users(user_id)` | Người dùng sở hữu thiết bị nhận push notification. |
| **`notification_db`**| `notification_deliveries` | `user_id` | `auth_db.users(user_id)` | Người nhận thông báo. |
