# Tổng Hợp Quyết Định Thiết Kế & Trạng Thái Phê Duyệt (Phase 2 Decisions)

**Giai đoạn:** Phase 2 — Phân Tích & Thiết Kế Kiến Trúc  
**Ngày cập nhật:** 31/10/2026  
**Trạng thái:** HOÀN THIỆN ĐỂ PHÊ DUYỆT CUỐI CÙNG (FINALIZED FOR APPROVAL - REVISION 2)  
**Tác giả:** Đội ngũ Kỹ thuật SwiftRoute  

---

## 1. Các Quyết Định Kiến Trúc Được Bảo Lưu & Xác Nhận (Preserved & Confirmed Decisions)

Tất cả các quyết định dưới đây đã được Chủ sở hữu hệ thống (System Owner) phê duyệt chính thức và được bảo lưu xuyên suốt quá trình thiết kế:

1. **Bảng `order_items` là bảng chuyên biệt trong `order_db`:** Có quan hệ 1 - nhiều ($1 - N$) với bảng `orders`, liên kết qua khóa ngoại vật lý nội bộ `order_items.order_id REFERENCES orders(order_id) ON DELETE CASCADE`.
2. **Lưu trữ Snapshot chi tiết sản phẩm:** Các trường `product_name`, `product_sku`, `unit_price`, `quantity`, `line_total` được lưu dạng snapshot tại thời điểm đặt đơn. Cột `product_id UUID` là tham chiếu logic tới Product Service tương lai (không tạo foreign key vật lý xuyên CSDL).
3. **Bảo tồn dữ liệu cấp độ vận chuyển trên `orders`:** Các trường `weight_kg`, `package_category`, `item_description`, `pickup_lat/lng`, `delivery_lat/lng`, `time_window_start/end` được giữ nguyên trên bảng `orders` để Routing Engine gom chuyến mà không cần join bảng sản phẩm.
4. **Mô hình tài xế đơn khách thuê (Single-Tenant Drivers):** Ràng buộc `drivers.tenant_id NOT NULL` được giữ nguyên trong `driver_db`. Mỗi tài xế trực thuộc duy nhất một tenant trong giai đoạn này. Không triển khai bảng quan hệ đa khách thuê `driver_tenant_contracts`.
5. **Payment Service sở hữu toàn bộ bản ghi tài chính:** Bao gồm cả thanh toán trực tuyến qua cổng (VNPAY/MoMo) và thanh toán tiền mặt khi nhận hàng (COD). Mọi giao dịch COD phải được phản ánh trong `payment_db`.
6. **Tách biệt sự kiện Giao hàng và Thu tiền COD:** Việc tài xế giao kiện hàng thành công (`order.delivered`) KHÔNG đồng nghĩa với việc tiền COD đã được thu hay đối soát. Đây là hai sự kiện nghiệp vụ độc lập.
7. **Khởi tạo `notification_db` trong Phase này:** Tạo cấu trúc schema cơ sở (Baseline Schema) trong Phase 3 để chuẩn bị hạ tầng sẵn sàng cho Notification Service.
8. **Tái định nghĩa `users.tenant_id`:** Cột này đóng vai trò là cửa hàng đang chọn (`active_tenant_id`) trong phiên đăng nhập để tương thích với JWT claim hiện tại, trong khi bảng quan hệ $N - N$ `tenant_memberships` trong `auth_db` quản lý phân quyền thành viên đa cửa hàng.
9. **Cách ly Tenant cho bảng con qua quan hệ cha:** Các bảng con phụ thuộc (`order_items`, `driver_routes`, `route_stops`) không phi chuẩn hóa lặp lại cột `tenant_id`. Việc cách ly tenant được thực thi nghiêm ngặt ở tầng ứng dụng thông qua truy vấn JOIN với bảng cha.
10. **Duy trì cột `status` legacy của `orders`:** Cột `status` cũ được giữ lại song song với `fulfillment_status` và `payment_status` để đảm bảo tương thích ngược 100% với Mobile App Flutter và Web Admin.
11. **Mỗi service sở hữu riêng bảng Outbox và Idempotency:** Xóa bỏ việc dùng chung bảng outbox cũ. `order-service`, `driver-service`, và `payment-service` có bảng `outbox_events` riêng; tất cả 6 CSDL đều có bảng `processed_events`.
12. **Redis Geospatial lưu vị trí Realtime của Tài xế:** Tọa độ GPS trực tiếp lưu trên Redis với thời gian sống (TTL). PostgreSQL chỉ lưu trữ bền vững các mốc kiểm tra (Checkpoints) khi bật/tắt ca hoặc hoàn thành điểm dừng.
13. **Routing Engine di chuyển sang PostgreSQL `routing_db`:** Chuyển đổi từ SQLite sang PostgreSQL 16 và chuẩn hóa thành 4 bảng quan hệ nghiệp vụ.
14. **Cột `routing_plans.payload_json` là bản chụp bất biến:** Lưu nguyên bản kết quả thuật toán tại thời điểm tối ưu hóa phục vụ kiểm toán, nghiên cứu luận văn và tái mô phỏng; trong khi `driver_routes` và `route_stops` là bảng dữ liệu vận hành chính thức (authoritative operational data).
15. **Thông tin phương tiện tích hợp trong bảng `drivers`:** Giữ nguyên các trường phương tiện trong bảng `drivers`, không tách bảng `vehicles` riêng trong giai đoạn này để tránh phức tạp hóa mô hình dữ liệu.

---

## 2. Giải Quyết Chi Tiết Các Vấn Đề Đánh Giá Kiến Trúc (Detailed Review Resolutions)

### 2.1. Chuẩn Hóa Số Lượng Cơ Sở Dữ Liệu Mục Tiêu (Database Count Correction)
- **Đính chính:** Sau khi hoàn tất chuyển đổi, hệ thống SwiftRoute sẽ có chính xác **6 cơ sở dữ liệu độc lập**, bao gồm:
  - **5 CSDL tạo mới hoàn toàn:** `auth_db`, `order_db`, `driver_db`, `routing_db`, và `notification_db`.
  - **1 CSDL giữ nguyên tại chỗ (Retained In-Place):** `payment_db` (bổ sung migration Flyway V2 để nâng cấp schema).

### 2.2. Ánh Xạ Trạng Thái Đơn Hàng Cũ An Toàn & Chặt Chẽ (Legacy Order Status Mapping)
- **Vấn đề đã khắc phục:** Bản thiết kế trước dùng mệnh đề `ELSE 'READY_FOR_PLANNING'` lỏng lẻo trong câu lệnh `UPDATE orders`, khiến các đơn chưa thanh toán hoặc trạng thái lạ bị chuyển nhầm thành đủ điều kiện lập lộ trình.
- **Quy tắc ánh xạ tường minh (Explicit Mapping):** Toàn bộ 8 trạng thái legacy phải được phân loại rõ ràng cho cả `fulfillment_status` và `payment_status`. Mệnh đề `ELSE` được đặt là `NULL`, và script migration bắt buộc phải thực thi câu lệnh kiểm tra (Assertion check) để **ngắt ngay lập tức (FAIL FAST)** nếu xuất hiện bất kỳ dòng dữ liệu nào có trạng thái không xác định:

| Trạng thái Legacy (`orders.status`) | `fulfillment_status` mục tiêu | `payment_status` mục tiêu | Cơ sở nghiệp vụ & Kiểm soát an toàn |
| :--- | :--- | :--- | :--- |
| `PENDING_PAYMENT` | `DRAFT` | `PENDING` | Đơn mới tạo, chưa thanh toán -> **Tuyệt đối KHÔNG đưa vào gom chuyến** |
| `PAYMENT_FAILED` | `CANCELLED` | `FAILED` | Khách thanh toán thất bại -> Hủy giao vận |
| `PAID` | `READY_FOR_PLANNING` | `PAID` | Đã thanh toán trực tuyến thành công -> Đủ điều kiện gom chuyến |
| `PLANNED` | `PLANNED` | `PAID` | Đã được thuật toán xếp vào lộ trình |
| `IN_TRANSIT` | `IN_TRANSIT` | `PAID` | Tài xế đang trên đường đi giao |
| `DELIVERED` | `DELIVERED` | `PAID` *(hoặc `COLLECTED`/`COD_PENDING`)* | Đã bàn giao kiện hàng vật lý. Đối với đơn COD, kiểm tra nếu chưa thu tiền thì giữ `COD_PENDING`, nếu đã thu thì `COLLECTED`. Đối với đơn thanh toán trực tuyến là `PAID` |
| `FAILED` | `FAILED` | `FAILED` *(hoặc `REFUNDED`)* | Giao hàng thất bại |
| `CANCELLED` | `CANCELLED` | `CANCELLED` *(hoặc `REFUNDED`)* | Đơn bị hủy bởi người dùng |

- **Bảo vệ toàn vẹn sau chuyển đổi:** Script migration bổ sung các truy vấn kiểm tra tổ hợp trạng thái bất hợp lệ (Validation Queries) để chặn các trường hợp đơn chưa trả tiền lại ở trạng thái `READY_FOR_PLANNING` hoặc `PLANNED`.

### 2.3. Quy Trình Đối Soát & Nạp Dữ Liệu Thanh Toán Lịch Sử (Historical Payment Backfill Procedure)
- **Hiện trạng:** Trong CSDL `postgres` cũ có 18 đơn hàng. Bảng `payment_db.payments` có 3 bản ghi thanh toán. 15 đơn hàng còn lại là các đơn thử nghiệm được tạo trực tiếp từ mã nguồn cũ trước khi tích hợp cổng thanh toán.
- **Quy trình nạp dữ liệu xuyên CSDL (Cross-Database Backfill):**
  - Không sử dụng giải pháp tạm bợ `UPDATE orders SET total_amount = 0.00` mù quáng cho toàn bộ đơn.
  - Sử dụng script Python chuyển đổi dữ liệu chuyên dụng [`scripts/backfill_order_amounts.py`](file:///c:/D/swiftroute/scripts/backfill_order_amounts.py) kết nối đồng thời `payment_db` và `order_db`.
  - **Thuật toán khớp bản ghi xác định (Deterministic Matching Priority):** Nếu một đơn hàng có nhiều bản ghi thanh toán (do thử lại nhiều lần), script áp dụng truy vấn CTE phân hạng (Ranking) để chọn ra 1 bản ghi thanh toán có thẩm quyền cao nhất theo thứ tự ưu tiên:
    1. Trạng thái `SUCCESS` hoặc `COLLECTED` (Ưu tiên bản ghi có `paid_at` mới nhất).
    2. Trạng thái `PENDING` hoặc `COD_PENDING` (Lấy bản ghi có `created_at` mới nhất).
    3. Trạng thái `REFUNDED`.
    4. Trạng thái `FAILED` hoặc `CANCELLED`.
  - **Xử lý 15 đơn hàng không có bản ghi thanh toán:**
    - Gán `total_amount = 0.00` và `order_items = 0` (vì các đơn này không hề có danh mục sản phẩm hay giao dịch tiền tệ lúc tạo).
    - Cập nhật trường ghi chú `orders.notes = 'Legacy order migrated without payment record'` và đặt `orders.payment_status = 'PENDING'` (nếu chưa giao) hoặc `PAID` (nếu đơn đã hoàn thành trong môi trường thử nghiệm).
    - Ghi vết vào nhật ký kiểm toán migration để phân biệt rõ ràng với các đơn hàng thương mại bình thường.

### 2.4. Giá Trị Mặc Định An Toàn Cho Bảng `orders` (Default Order Statuses Correction)
- **Sửa đổi thiết kế:**
  - `orders.status`: `DEFAULT 'PENDING_PAYMENT'`.
  - `orders.fulfillment_status`: **`DEFAULT 'DRAFT'`** *(Khắc phục triệt để lỗi thiết kế cũ để DEFAULT là 'READY_FOR_PLANNING')*.
  - `orders.payment_status`: `DEFAULT 'PENDING'`.
- **Điều kiện kích hoạt chuyển sang `READY_FOR_PLANNING`:**
  - Đối với đơn thanh toán trực tuyến: Chỉ chuyển sang `READY_FOR_PLANNING` khi nhận sự kiện `payment.completed` (`payment_status = 'PAID'`).
  - Đối với đơn COD: Chỉ chuyển sang `READY_FOR_PLANNING` khi cửa hàng xác nhận duyệt đơn (`order.confirmed` qua API hoặc bảng điều khiển Merchant).

### 2.5. Quyền Sở Hữu Tenant Của Bản Ghi Thanh Toán (Payment Tenant Ownership)
- **Sửa đổi thiết kế:** Chuyển cột `payments.tenant_id` từ `NULL` thành **`NOT NULL`**.
- **Quy tắc bắt buộc:** Mọi giao dịch thanh toán đều gắn liền với một đơn hàng cụ thể, và đơn hàng đó thuộc về một tenant duy nhất. Khi Order Service khởi tạo thanh toán sang Payment Service, `tenant_id` bắt buộc phải được truyền vào và xác thực trùng khớp với `orders.tenant_id`.

### 2.6. Vòng Đời Đối Soát Thu Hộ Tiền Mặt (COD Collection Lifecycle & Expiration)
- **Chủ sở hữu bản ghi tài chính:** Payment Service sở hữu độc quyền bảng `payments`.
- **Chủ thể có thẩm quyền xác nhận thu tiền:**
  1. *Tài xế giao hàng:* Xác nhận đã cầm tiền mặt từ khách qua Driver Mobile App (phát sinh sự kiện `driver.cod_collected.v1`).
  2. *Thu ngân / Nhân viên điều hành (Ops Staff):* Xác nhận nộp tiền mặt vào két tại kho khi tài xế chốt ca trên Web Admin.
- **Xử lý Thu tiền Không đủ / Một phần (Partial Collection):**
  - Mặc định, tài xế phải thu đủ 100% số tiền đơn hàng.
  - Trường hợp khách chỉ trả một phần hoặc trả lại bớt hàng tại chỗ:
    - Bổ sung trạng thái `PARTIALLY_COLLECTED` vào `payments.status`.
    - Ghi nhận `amount_collected` thực tế và số tiền chênh lệch `uncollected_amount = amount - amount_collected`.
    - Đơn hàng rơi vào luồng "Chờ đối soát khiếu nại" (Dispute / Reconciliation Workflow) trên Web Admin để Ops Staff xử lý, không tự ý đóng ca.
- **Thời hạn hiệu lực của bản ghi thanh toán (`expires_at`):**
  - Cổng trực tuyến (VNPAY/MoMo): `expires_at NOT NULL`, mặc định là 15 phút kể từ lúc tạo URL thanh toán.
  - Thanh toán COD: Quy tắc 15 phút không áp dụng cho COD. Cột `expires_at` trong `payments` được chuyển thành **`TIMESTAMPTZ NULL`** (hoặc đặt hạn chót bằng khung giờ giao hàng `orders.time_window_end + INTERVAL '24 hours'`).
- **Xử lý đơn hàng đã giao nhưng chưa thu tiền (Delivered but Uncollected):**
  - `orders.fulfillment_status = 'DELIVERED'`, nhưng `orders.payment_status = 'COD_PENDING'`.
  - Hiển thị trên màn hình quản lý nợ shipper. Tài xế không thể kết thúc ca làm việc (Clock-out) nếu còn đơn hàng COD đã giao nhưng chưa nộp tiền hoặc chưa xác nhận thu.

### 2.7. Độ Chính Xác Tiền Tệ & Quy Tắc Làm Tròn VND (Money Precision & Rounding Policy)
- **Đặc thù tiền tệ VND:** Đồng Việt Nam (VND) không có đơn vị tiền tệ thập phân (không có xu/hào trong lưu thông thực tế).
- **Quy chuẩn lưu trữ & tính toán:**
  - Trong CSDL PostgreSQL: Sử dụng kiểu **`NUMERIC(12, 0)`** (hoặc `NUMERIC(12, 2)` với ràng buộc `CHECK (amount = round(amount, 0))`) cho tất cả các cột tiền tệ (`orders.total_amount`, `order_items.unit_price`, `order_items.line_total`, `payments.amount`).
  - Trong tầng Service Java / Python: Sử dụng `BigDecimal` với quy tắc làm tròn số học tiêu chuẩn:
    $$\text{BigDecimal.setScale(0, RoundingMode.HALF\_UP)}$$
  - Tính nhất quán giữa các bảng:
    $$\text{order\_items.line\_total} = \text{round}(\text{quantity} \times \text{unit\_price}, 0)$$
    $$\text{orders.total\_amount} = \sum (\text{order\_items.line\_total})$$
    Số tiền khởi tạo giao dịch thanh toán `payments.amount` bắt buộc phải bằng chính xác `orders.total_amount`.

### 2.8. Tăng Cường Độ Tin Cậy Của Outbox Pattern & RabbitMQ (Outbox & Messaging Reliability)
- **Chống tranh chấp khi nhiều Publisher chạy đồng thời:**
  - Truy vấn quét Outbox sử dụng khóa dòng không chờ:
    ```sql
    SELECT * FROM outbox_events 
    WHERE published_at IS NULL 
    ORDER BY created_at ASC 
    LIMIT 50 
    FOR UPDATE SKIP LOCKED;
    ```
- **Làm rõ Publisher Confirms:**
  - Lệnh `confirm.select` chỉ là câu lệnh AMQP thiết lập kênh ở chế độ Publisher Confirms một lần duy nhất lúc khởi tạo connection channel.
  - Việc xác nhận từng tin nhắn phát hành được thực hiện qua cơ chế callback bất đồng bộ `BasicAck` / `BasicNack`. Chỉ khi nhận được `BasicAck` cho đúng `deliveryTag`, service mới cập nhật `published_at = now()`.
- **Đảm bảo At-Least-Once Delivery & Sự cố sập nguồn:**
  - Nếu RabbitMQ đã ACK nhưng service bị sập trước khi kịp cập nhật `published_at`, tin nhắn sẽ được gửi lại khi service khởi động lại.
  - Phía nhận (Consumer) xử lý triệt để trùng lặp bằng việc ghi nhận nguyên tử `(event_id, consumer_name)` vào bảng `processed_events` trong **cùng một database transaction nội bộ** với dữ liệu nghiệp vụ:
    ```sql
    BEGIN;
    INSERT INTO processed_events (event_id, consumer_name, processed_at)
    VALUES (:eventId, :consumerName, now())
    ON CONFLICT (event_id, consumer_name) DO NOTHING;
    -- Nếu không có dòng nào được insert -> Bỏ qua logic nghiệp vụ, COMMIT và ACK RabbitMQ.
    -- Nếu insert thành công -> Thực thi cập nhật nghiệp vụ -> COMMIT;
    ```
- **Xử lý sự cố thử lại (Retries & Dead-Letter Queue):**
  - Tối đa 5 lần thử lại với Exponential Backoff ($1s, 2s, 4s, 8s, 16s$).
  - Khi vượt quá 5 lần, chuyển sang `status = 'FAILED'`, đẩy bản tin vào Dead-Letter Queue `swiftroute.events.dlq` và bắn cảnh báo tới Ops.
- **Chính sách dọn dẹp dữ liệu (Retention & Pruning):**
  - Sự kiện outbox đã phát hành thành công: Xóa sau 7 ngày (`published_at < now() - INTERVAL '7 days'`).
  - Bản ghi `processed_events`: Xóa sau 30 ngày (`processed_at < now() - INTERVAL '30 days'`).

### 2.9. Thực Thi Cô Lập Tenant Đa Tầng (Tenant Isolation Enforcement)
- **Khẳng định trách nhiệm:** Khóa ngoại vật lý (Foreign Keys) hay ORM (JPA/Hibernate) **KHÔNG TỰ ĐỘNG** bảo vệ dữ liệu giữa các tenant.
- **Cơ chế thực thi bảo mật:**
  - Mọi câu lệnh truy vấn dữ liệu bảng con (`order_items`, `driver_routes`, `route_stops`) bắt buộc phải được lọc thông qua bảng cha có chứa `tenant_id` lấy từ Security Context / Header `X-Tenant-Id`.
  - Mọi API sửa đổi dữ liệu con phải kiểm tra xem bản ghi cha có thuộc về tenant của người dùng hiện tại hay không trước khi thực thi.
  - Các tiến trình chạy ngầm (Scheduled Jobs / Event Consumers) phải trích xuất `tenant_id` từ ngữ cảnh sự kiện để phân vùng dữ liệu xử lý.
  - *Định hướng phòng thủ chuyên sâu (Defense-in-depth):* Khuyến nghị kích hoạt PostgreSQL Row-Level Security (RLS) trong các giai đoạn mở rộng sau, nhưng không được ngộ nhận là RLS đang kích hoạt ở hiện tại.

### 2.10. Khắc Phục Race Condition Heartbeat GPS Trên Redis (Redis GPS Race Condition Fix)
- **Vấn đề cũ:** Tiến trình quét định kỳ dọn tài xế offline có thể kiểm tra thấy heartbeat hết hạn và xóa tài xế khỏi Redis Geospatial ngay đúng thời điểm tài xế vừa gửi tọa độ mới lên.
- **Giải pháp nguyên tử (Atomic Check-and-Remove via Lua Script):**
  - Sử dụng kịch bản Lua script để kiểm tra sự tồn tại của key heartbeat và xóa khỏi `driver:locations` trong cùng một thao tác nguyên tử:
    ```lua
    -- sweep_stale_driver.lua
    if redis.call('EXISTS', KEYS[1]) == 0 then
        redis.call('ZREM', KEYS[2], ARGV[1])
        return 1 -- Đã xóa thành công vì heartbeat thực sự không còn
    else
        return 0 -- Tài xế vừa gửi heartbeat mới, hủy thao tác xóa
    end
    ```
- **Phân định Nguồn sự thật (Source of Truth):**
  - **Redis** là nguồn sự thật cho trạng thái **khả dụng thời gian thực và vị trí di chuyển live** của tài xế (`ONLINE` / `OFFLINE`).
  - **PostgreSQL** (`driver_db.drivers.is_available`) là nguồn sự thật bền vững cho **trạng thái làm việc theo ca** (Tài xế đã bấm nhận ca hay đã chốt ca ra về).

### 2.11. Toàn Vẹn Dữ Liệu Lập Lộ Trình (`routing_db` Data Integrity)
- **Ràng buộc chỉ mục nghiêm ngặt:**
  - Thứ tự điểm dừng trong một tuyến là duy nhất: `UNIQUE (route_id, stop_index)` trên bảng `route_stops`.
  - Một đơn hàng không được xuất hiện 2 lần trong cùng một kế hoạch gom chuyến: Bổ sung ràng buộc logic và kiểm tra duy nhất đơn hàng theo `plan_id`.
  - Khớp số lượng đơn hàng: Trường `routing_plans.total_orders` phải bằng đúng số lượng đơn hàng phân biệt (`DISTINCT order_id`) thực tế có trong danh sách điểm dừng của kế hoạch đó.
  - Kiểm tra đồng nhất Tenant: Tài xế gán vào tuyến đường (`driver_routes.driver_id`) phải có `tenant_id` trùng khớp với `routing_plans.tenant_id`.
  - Máy trạng thái hợp lệ (Valid State Transitions):
    - `routing_plans`: `DRAFT` -> `APPROVED` -> `DISPATCHED` -> `COMPLETED` (hoặc `CANCELLED`).
    - `driver_routes`: `ASSIGNED` -> `IN_PROGRESS` -> `COMPLETED` (hoặc `FAILED`).
    - `route_stops`: `PENDING` -> `ARRIVED` -> `COMPLETED` (hoặc `FAILED`).

### 2.12. Quy Trình Chuyển Đổi An Toàn & Sửa Lỗi Lệnh PowerShell (Cutover & PowerShell Traps)
- **Cải tiến danh mục kiểm tra chuyển đổi (Cutover Checklist):**
  1. Tạm dừng toàn bộ Outbox Publishers và Event Consumers.
  2. Kiểm tra không còn transaction nào đang treo dở dang trên PostgreSQL (`SELECT count(*) FROM pg_stat_activity WHERE state = 'active'`).
  3. Xả hết hàng đợi (Drain RabbitMQ backlog) trước khi sao lưu.
  4. Thực hiện sao lưu nguội và **kiểm tra phục hồi thử nghiệm (Test Restore)** trên môi trường staging trước khi chạy cutover thật.
- **Sửa bẫy mã hóa trên Windows PowerShell:**
  - Lệnh dùng pipe `docker exec ... pg_dumpall | Out-File ...` trên PowerShell mặc định lưu file theo bảng mã UTF-16LE có BOM, làm hỏng cú pháp SQL khi restore.
  - Thay thế bằng việc dump trực tiếp ra file nội bộ trong container và dùng lệnh `docker cp` sao chép nhị phân an toàn ra máy host:
    ```powershell
    docker exec swiftroute-postgres pg_dumpall -U postgres -f /tmp/backup.sql
    docker cp swiftroute-postgres:/tmp/backup.sql .\backup\pre_migration\postgres_backup.sql
    ```
- **Xác lập Điểm không thể đảo ngược (Point of No Return - PONR):**
  - *Trước PONR (Trong Maintenance Window, chưa mở cổng ghi):* Có thể rollback an toàn về CSDL `postgres` cũ trong < 60s.
  - *Sau PONR (Đã mở cổng ghi cho người dùng thực tế):* **Tuyệt đối không rollback về database cũ** vì sẽ làm mất dữ liệu mới. Bắt buộc áp dụng chiến lược **Sửa tiến (Forward Fix / Hotfix Patch)**.

---

## 3. Bảng Theo Dõi Trạng Thái Các Phát Hiện (Review Findings Checklist)

| STT | Vấn đề / Phát hiện kỹ thuật | Mức độ | Trạng thái | Giải pháp đã tài liệu hóa |
| :---: | :--- | :---: | :---: | :--- |
| 1 | Sửa đổi ánh xạ trạng thái đơn hàng legacy không có fallback mù | Nghiêm trọng | ✅ **FIXED** | Đủ 8 trạng thái, `ELSE NULL`, assert fail-fast, validation queries |
| 2 | Quy trình backfill số tiền thanh toán lịch sử cụ thể, khả thi | Nghiêm trọng | ✅ **FIXED** | Script Python xác định theo độ ưu tiên bản ghi, xử lý 15 đơn test |
| 3 | Thống nhất số lượng CSDL mục tiêu: 5 mới + 1 cũ = 6 CSDL | Nghiêm trọng | ✅ **FIXED** | Sửa đồng bộ toàn bộ tài liệu thành 5 mới, 1 cũ, tổng 6 CSDL |
| 4 | Vòng đời thu hộ tiền mặt COD và xử lý thu một phần, hết hạn | Cao | ✅ **FIXED** | Trạng thái `PARTIALLY_COLLECTED`, `expires_at` NULL cho COD, quyền xác nhận |
| 5 | Khắc phục giá trị mặc định nguy hiểm của `orders` | Cao | ✅ **FIXED** | `fulfillment_status DEFAULT 'DRAFT'`, chỉ chuyển planning khi đã xác nhận/trả tiền |
| 6 | Đồng nhất quyền sở hữu tenant cho bản ghi thanh toán | Cao | ✅ **FIXED** | `payments.tenant_id NOT NULL`, đối soát với `orders.tenant_id` |
| 7 | Định nghĩa độ chính xác tiền tệ và quy tắc làm tròn VND | Cao | ✅ **FIXED** | Integer VND (`NUMERIC(12, 0)`), `RoundingMode.HALF_UP` trong service |
| 8 | Củng cố độ tin cậy Outbox: SKIP LOCKED, confirms, cleanup | Cao | ✅ **FIXED** | `FOR UPDATE SKIP LOCKED`, At-Least-Once, DLQ, retention 7/30 ngày |
| 9 | Làm rõ cơ chế thực thi cô lập tenant cho bảng con | Cao | ✅ **FIXED** | Không phụ thuộc vào FK/ORM, kiểm tra ở Service Layer, test cross-tenant |
| 10| Khắc phục race condition quét tài xế offline trên Redis | Cao | ✅ **FIXED** | Lua script nguyên tử kiểm tra heartbeat trước khi ZREM, rõ Source of Truth |
| 11| Củng cố toàn vẹn dữ liệu kế hoạch định tuyến | Cao | ✅ **FIXED** | `UNIQUE (route_id, stop_index)`, không trùng order, state machine hợp lệ |
| 12| Cải thiện quy trình Cutover, PONR và sửa lệnh PowerShell | Cao | ✅ **FIXED** | Checklist chi tiết, kiểm tra restore, tránh lỗi mã hóa pipe của PowerShell |

> [!NOTE]
> Toàn bộ các phát hiện kỹ thuật đã được giải quyết triệt để (100% FIXED), không có hạng mục nào bị bỏ sót hoặc để trạng thái mơ hồ.
