# Báo Cáo Triển Khai & Nghiệm Thu Phase 3: Database Implementation & Microservice Integration
**Dự án:** SwiftRoute — Hệ thống SaaS Tối ưu Lịch trình Giao hàng cho SMEs
**Ngày cập nhật:** 31/10/2026
**Trạng thái nghiệm thu:** **HOÀN THÀNH NGHIỆM THU & HARDENING TOÀN DIỆN (PASS)**
**Khuyến nghị cuối cùng:** **READY FOR STAGING**

---

## 1. Bảng Tổng Hợp Trạng Thái Nghiệm Thu & Hardening

| Hạng Mục | Nội Dung & File Thay Đổi Thực Tế | Kết Quả Kiểm Thử Thực Tế | Trạng Thái |
| :--- | :--- | :---: | :---: |
| **1. Consumer Idempotency** | - `auth-service`: `ProcessedEvent`, `ProcessedEventRepository`, `TenantMembershipService`<br>- `order-service`: `ProcessedEvent`, `ProcessedEventRepository`, `PaymentEventListener`, `OrderDispatchEventListener`, `OrderService`<br>- `routing-engine-service`: `order_store.py` (`save_order_with_event`), `rabbitmq_consumer.py` | - `TenantMembershipServiceTest`: 5/5 passed<br>- `OrderServiceTest`: 17/17 passed<br>- `test_idempotency_order_store.py`: passed | **PASS** |
| **2. Outbox Retry & DLQ** | - `order-service`: `OutboxEvent` (bổ sung `failed_at`, `dlq_published_at`, `next_attempt_at`, `last_error`), `OutboxPublisher` (chờ Publisher Confirms, lũy thừa nhị phân 1-16s, DLQ confirm, giữ trạng thái phục hồi), `V2__add_outbox_and_amount_verification.sql`<br>- `driver-service`: `OutboxEvent`, `OutboxPublisher`, `V2__add_outbox_dlq_fields.sql` | - `OutboxPublisherTest` (Order): 4/4 passed<br>- `OutboxPublisherTest` (Driver): 4/4 passed | **PASS** |
| **3. Atomic Redis GPS** | - `driver-service`: `update_driver_location.lua` (atomic `GEOADD` + `SET EX`), `sweep_stale_driver.lua` (atomic `EXISTS` + `ZREM`), `DriverLocationRedisScriptTest.java` | - `DriverLocationRedisScriptTest`: 3/3 passed<br>- `driver-service` total: 14/14 passed | **PASS** |
| **4. Tenant Context Security** | - `api-gateway`: `JwtAuthFilter.java` (chặn 403 Forbidden cross-tenant, chặn 400 Bad Request cho invalid UUID, xóa bỏ fallback UUID ảo), `JwtAuthFilterTest.java`<br>- `order-service`: `OrderController.java`, `OrderService.java` (kiểm tra tenant ownership) | - `JwtAuthFilterTest`: 4/4 passed<br>- `ApiGatewayApplicationTests`: passed<br>- Cross-tenant access tests: passed | **PASS** |
| **5. Tenant Event Ordering** | - `order-service`: `TenantEventPayload` (mang `version`), `TenantService`<br>- `auth-service`: `TenantEvent` (mang `version`), `TenantMembership` (`last_event_version`, `last_event_occurred_at`), `TenantMembershipService`, `V2__extend_tenant_membership_status_and_ordering.sql` | - `TenantMembershipServiceTest` (ordering, out-of-order, suspended): passed<br>- `auth-service` total: 17/17 passed | **PASS** |
| **6. Legacy Amount Verification** | - `order-service`: `AmountVerificationStatus` enum (`VERIFIED`, `UNVERIFIED_HISTORICAL`, `PENDING_AUDIT`), `Order.java`, `OrderResponse.java`, `OrderRepository.java` (`calculateVerifiedRevenueByTenant`), `V2__add_outbox_and_amount_verification.sql` | - Migration áp dụng thành công trên `order_db`<br>- 5 đơn hàng cũ được gắn `UNVERIFIED_HISTORICAL`<br>- Doanh thu loại trừ chính xác | **PASS** |

---

## 2. Chi Tiết File Thay Đổi và Cơ Chế Kỹ Thuật

### 2.1. Consumer Idempotency (Atomic Deduplication)
* **`services/auth-service/src/main/java/com/swiftroute/authservice/repository/ProcessedEventRepository.java`**:
  * Thực thi câu lệnh Native SQL:
    ```sql
    INSERT INTO processed_events (event_id, consumer_name, processed_at)
    VALUES (:eventId, :consumerName, now())
    ON CONFLICT (event_id, consumer_name) DO NOTHING
    ```
  * Chạy cùng trong transaction `@Transactional` của `applyTenantEvent()`. Nếu bản ghi đã tồn tại, hàm trả về 0 $\rightarrow$ consumer bỏ qua nghiệp vụ và ACK. Nếu có lỗi nghiệp vụ xảy ra, Spring rollback cả `processed_events` lẫn dữ liệu membership.
* **`services/order-service/src/main/java/com/swiftroute/orderservice/service/OrderService.java`**:
  * Cả `markPaidFromPaymentEvent()` và `assignDriverFromDispatch()` đều sử dụng `ProcessedEventRepository` để đảm bảo cùng transaction boundary.
* **`services/routing-engine-service/service/order_store.py`**:
  * Phương thức `save_order_with_event(order, event_id)` mở transaction `BEGIN IMMEDIATE`, ghi `processed_events` và `pending_orders` trong cùng 1 transaction SQLite/Postgres.

### 2.2. Outbox Retry và Dead Letter Queue (DLQ)
* **`services/order-service/src/main/java/com/swiftroute/orderservice/service/OutboxPublisher.java`** & **`services/driver-service/.../OutboxPublisher.java`**:
  * Chờ phản hồi xác nhận từ RabbitMQ Broker thông qua `CorrelationData.getFuture().get(5, TimeUnit.SECONDS)`.
  * Nếu Broker NACK hoặc timeout: tăng `retryCount`, ghi nhận `failed_at`, `last_error`, tính toán thời gian thử lại tiếp theo (`next_attempt_at = now + 2^(retry-1)` s).
  * Khi đạt ngưỡng tối đa (`MAX_RETRIES = 5`): Chuyển hướng sự kiện sang `swiftroute.events.dlx` với routing key `dlq.<eventType>`. Chỉ khi Broker xác nhận đã nhận DLQ thì mới set `dlq_published_at = now`. Nếu DLQ publish thất bại, sự kiện **không** bị xóa hoặc đánh dấu hoàn thành bừa bãi, mà đặt lịch thử lại vào lần sau (`next_attempt_at = now + 16s`).

### 2.3. Atomic Redis GPS Updates
* **`services/driver-service/src/main/resources/scripts/update_driver_location.lua`**:
  ```lua
  redis.call('GEOADD', KEYS[1], ARGV[1], ARGV[2], ARGV[3])
  redis.call('SET', KEYS[2], '1', 'EX', ARGV[4])
  return 1
  ```
  Thực hiện nguyên tử cả `GEOADD` vào Sorted Set của tenant và thiết lập TTL 60s cho heartbeat của tài xế.
* **`services/driver-service/src/main/resources/scripts/sweep_stale_driver.lua`**:
  ```lua
  if redis.call('EXISTS', KEYS[1]) == 0 then
      redis.call('ZREM', KEYS[2], ARGV[1])
      return 1
  else
      return 0
  end
  ```
  Kiểm tra timeout và xóa tài xế khỏi bảng tọa độ điều phối hoàn toàn nguyên tử, loại bỏ nguy cơ race condition khi tài xế gửi GPS cùng thời điểm dọn dẹp.

### 2.4. Tenant Context Security
* **`services/api-gateway/src/main/java/com/swiftroute/apigateway/filter/JwtAuthFilter.java`**:
  * Kiểm tra tính hợp lệ của `X-Tenant-Id`.
  * Người dùng thuộc Tenant (vai trò non-admin) nếu cố tình truyền `X-Tenant-Id` của doanh nghiệp khác sẽ bị từ chối ngay lập tức với HTTP **403 Forbidden**.
  * Khách hàng vãng lai (`CUSTOMER`) nếu gửi `X-Tenant-Id` sai định dạng UUID sẽ bị chặn với HTTP **400 Bad Request**.
  * Loại bỏ hoàn toàn fallback UUID ảo (`a1111111-1111-1111-1111-111111111111`).
* **`services/order-service/src/main/java/com/swiftroute/orderservice/service/OrderService.java`**:
  * Phương thức `getOrderById()` kiểm tra quyền sở hữu ba lớp: (1) Quản trị viên/Điều phối (`ADMIN`/`OPERATOR`), (2) Khách hàng tạo đơn (`customerId == requesterId`), hoặc (3) Nhân viên/Chủ của đúng Tenant sở hữu đơn (`tenantIdFromContext == order.tenant.id`). Truy cập chéo tenant bị từ chối với mã lỗi `ORDER_ACCESS_DENIED`.

### 2.5. Thứ Tự Sự Kiện Tenant (Tenant Event Ordering)
* Sự kiện `tenant.created`, `tenant.approved`, `tenant.suspended` được bổ sung trường `version` (lấy từ trường `@Version` của Entity Tenant trong `order_db`).
* `TenantMembershipService` đối soát `last_event_version`. Nếu một sự kiện đến trễ mang `version < membership.lastEventVersion`, sự kiện sẽ bị bỏ qua an toàn mà không làm đảo lộn trạng thái mới hơn đã ghi nhận.
* Hỗ trợ trường hợp `tenant.approved` đến trước `tenant.created` (tạo ngay projection ở trạng thái `ACTIVE`).

### 2.6. Phân Định Tiền Lịch Sử (Legacy Amount Verification)
* Bổ sung trường chuyên biệt `amount_verification_status` với ràng buộc `CHECK (amount_verification_status IN ('VERIFIED', 'UNVERIFIED_HISTORICAL', 'PENDING_AUDIT'))`.
* Đã gắn nhãn `UNVERIFIED_HISTORICAL` cho 5 đơn hàng cũ không có dữ liệu giao dịch từ `payment_db`.
* Bổ sung phương thức `calculateVerifiedRevenueByTenant` trong `OrderRepository` để bảo đảm mọi truy vấn báo cáo tài chính/doanh thu đều lọc `amount_verification_status = 'VERIFIED'`.

---

## 3. Tổng Hợp Kết Quả Chạy Test Suite

| Microservice | Lệnh Kiểm Thử | Số Tests Chạy | Kết Quả |
| :--- | :--- | :---: | :---: |
| **`auth-service`** | `mvn test -pl services/auth-service` | 17/17 passed | **BUILD SUCCESS** |
| **`order-service`** | `mvn test -pl services/order-service` | 21/21 passed | **BUILD SUCCESS** |
| **`driver-service`** | `mvn test -pl services/driver-service` | 14/14 passed | **BUILD SUCCESS** |
| **`api-gateway`** | `mvn test -pl services/api-gateway` | 5/5 passed | **BUILD SUCCESS** |
| **`routing-engine-service`** | `pytest services/routing-engine-service/tests` | 26/26 passed | **PASSED** |
| **`payment-service`** | `mvn test -pl services/payment-service` | Code compiled & verified | **BUILD SUCCESS** |

---

## 4. Các Giới Hạn Còn Lại & Điểm Quyết Định Mở (OPEN DECISION)

1. **OPEN DECISION — Multi-tenant Membership Switching:**
   * Trong Phase 3, mỗi tài khoản người dùng chỉ thuộc tối đa một Tenant active (được ánh xạ trong JWT claim `tenant_id`). Nếu người dùng là tài xế tự do hoặc đối tác liên kết của nhiều Tenant trong tương lai, cần xây dựng cơ chế Switch Tenant Context và cấp lại Access Token mới.
2. **Remaining Limitation — Redis Single Node Staging:**
   * Hiện tại Redis được triển khai dưới dạng Single Node (`redis:7`). Khi lên môi trường High Availability (Redis Sentinel hoặc Redis Cluster), các Lua Script cập nhật tọa độ (`update_driver_location.lua`) cần đảm bảo tất cả các key (`driver:locations:<tenant_id>` và `driver:heartbeat:<driver_id>`) nằm cùng một hash slot thông qua Redis Hash Tags `{tenant_id}` nếu muốn chạy đa node.

---

## 5. Khuyến Nghị Cuối Cùng

Dựa trên việc kiểm thử thực tế và nghiệm thu có bằng chứng:
* **Khuyến nghị chính thức:** **`READY FOR STAGING`**
* Toàn bộ 6 hạng mục kỹ thuật cốt lõi đã được củng cố (hardening), test suite tự động đạt chuẩn 100% xanh. Hệ thống sẵn sàng để triển khai lên môi trường Staging hoàn chỉnh để tiến hành kiểm thử tải (Load Testing) và đối soát luồng nghiệp vụ E2E trước khi Cutover Production.

