# Thiết kế cơ sở dữ liệu microservices — SwiftRoute

## 1. Phạm vi và quyết định kiến trúc

Tài liệu này hiện thực hoá kế hoạch `ke-hoach-trien-khai.md`: SaaS lập kế hoạch giao hàng đa tenant, gồm Auth, Order, Driver, Routing Engine, Notification và API Gateway. PostgreSQL là transactional store chuẩn; RabbitMQ là event bus; Redis chỉ là cache/presence/geo-state có thể mất dữ liệu. Mọi thời gian lưu ở `TIMESTAMPTZ` (UTC), mọi định danh liên service là UUID/UUIDv7 do service chủ sở hữu sinh ra.

**Quy tắc bất biến:** mỗi service sở hữu một database (ít nhất database và database role riêng), chỉ service đó được cấp quyền đọc/ghi schema của mình. Không service nào truy vấn database khác, kể cả báo cáo. Khoá tham chiếu tới dữ liệu của service khác chỉ là UUID có kiểm tra nghiệp vụ qua API hoặc event; không tạo foreign key vật lý xuyên ranh giới service.

```
Client -> API Gateway -> Auth / Order / Driver / Routing / Notification
                           |       |        |          |
                          DB      DB       DB         DB
                           \       |        |         /
                            ---- RabbitMQ events -----
                                      |
                                  Notification DB
```

`order-service` tiếp tục là chủ sở hữu tenant trong giai đoạn hiện tại vì tenant onboarding đã nằm ở đó. Nếu sau này có billing, catalogue hoặc nhiều domain dùng tenant configuration, tách thành **Tenant Service** là một thay đổi độc lập; không chuyển ownership âm thầm trong đợt này.

## 2. Chuẩn dữ liệu dùng chung

| Quy ước | Quy định |
|---|---|
| Khoá chính | `UUID PRIMARY KEY`; ứng dụng sinh UUID để có thể ghi aggregate và outbox trong một transaction. |
| Tenant isolation | Bảng có dữ liệu nghiệp vụ phải có `tenant_id UUID NOT NULL`, index bắt đầu bằng `tenant_id`, và tenant ID lấy từ JWT/gateway đã xác thực, không tin payload client. |
| Tiền/tải trọng | `NUMERIC(12,2)`/`NUMERIC(10,3)`, không dùng `DOUBLE PRECISION` cho giá trị cần tính chính xác. Toạ độ dùng `NUMERIC(9,6)` hoặc PostGIS `geography(Point,4326)`. |
| Enum | `VARCHAR` + `CHECK`, để triển khai độc lập và mở rộng an toàn hơn PostgreSQL enum dùng chung. |
| Audit/concurrency | `created_at`, `updated_at`, `version BIGINT NOT NULL DEFAULT 0`; cập nhật phải tăng version (optimistic locking). |
| PII | Email/điện thoại/địa chỉ chi tiết chỉ lưu ở service cần thiết; mã hoá at-rest theo hạ tầng, hash refresh token, không đưa PII vào event/log. |
| Xoá | Aggregate giao dịch dùng soft-delete/status; retention và purge bất đồng bộ. Không cascade delete xuyên aggregate. |

Các `CHECK` và physical foreign key **được dùng trong cùng database/service** để bảo vệ bất biến cục bộ; cấm chỉ áp dụng với ranh giới giữa service.

## 3. Ownership, entities và lược đồ

### 3.1 Auth Service — `auth_db`

Chủ sở hữu: danh tính, mật khẩu, phiên đăng nhập và quyền người dùng. Auth không sở hữu tenant; `tenant_id` trong membership là logical reference tới Order.

| Bảng | Cột chính (kiểu, ràng buộc) | Index/quan hệ nội bộ |
|---|---|---|
| `users` | `user_id UUID PK`; `email CITEXT NOT NULL UNIQUE`; `phone VARCHAR(20)`; `password_hash VARCHAR(255) NOT NULL`; `full_name VARCHAR(255)`; `global_role VARCHAR(30) NOT NULL CHECK (...)`; `status VARCHAR(20) NOT NULL CHECK (...)`; `created_at`, `updated_at TIMESTAMPTZ`; `version BIGINT` | unique email; index `(status, created_at)` cho admin. Bỏ `tenant_id` khỏi user vì một user có thể thuộc nhiều tenant. |
| `tenant_memberships` | `membership_id UUID PK`; `tenant_id UUID NOT NULL` (logical); `user_id UUID NOT NULL FK users`; `membership_role VARCHAR(30) NOT NULL`; `status VARCHAR(20) NOT NULL`; timestamps, `version` | `UNIQUE(tenant_id,user_id)`; `(user_id,status)`; `(tenant_id,status)`. FK chỉ tới `users` cùng Auth DB. |
| `refresh_tokens` | `token_id UUID PK`; `user_id UUID NOT NULL FK users`; `token_hash CHAR(64) NOT NULL UNIQUE`; `family_id UUID NOT NULL`; `issued_at`, `expires_at TIMESTAMPTZ NOT NULL`; `revoked_at`; `replaced_by_token_id UUID`; `device_info`, `ip_address INET` | `(user_id, revoked_at, expires_at)`; `(family_id)`. Token raw tuyệt đối không lưu. |
| `processed_events` | `consumer_name VARCHAR(100)`, `event_id UUID`, `processed_at TIMESTAMPTZ`; PK `(consumer_name,event_id)` | Idempotency cho consumer event tenant. TTL/purge 90 ngày. |

Auth phát hành JWT có `sub`, `global_role`, và các tenant membership active (hoặc gateway/API truy vấn entitlement cache); claim không được là nguồn sự thật dài hạn. Khi nhận `tenant.created/approved/suspended`, Auth upsert membership idempotently.

### 3.2 Order Service — `order_db`

Chủ sở hữu: tenant onboarding, đơn giao hàng và vòng đời đơn. Đây là source of truth của `tenant_id`, order status và yêu cầu giao.

| Bảng | Cột chính (kiểu, ràng buộc) | Index/quan hệ nội bộ |
|---|---|---|
| `tenants` | `tenant_id UUID PK`; `owner_user_id UUID NOT NULL` (logical Auth); `business_name VARCHAR(255) NOT NULL`; `business_type VARCHAR(50) CHECK`; `address VARCHAR(500)`; `pickup_point geography(Point,4326)` hoặc `pickup_lat/lng NUMERIC(9,6)` với range checks; `optimization_weights JSONB NOT NULL`; `subscription_plan VARCHAR(30)`; `status VARCHAR(20) CHECK`; approval fields; timestamps, `version` | `UNIQUE(lower(trim(business_name)))` chỉ nếu tên phải toàn cục; nếu không dùng `UNIQUE(owner_user_id, lower(trim(business_name)))`; `(owner_user_id,status)`. |
| `orders` | `order_id UUID PK`; `tenant_id UUID NOT NULL FK tenants`; `customer_user_id UUID NOT NULL` (logical Auth); `external_reference VARCHAR(100)`; pickup/delivery address snapshot + geo points; `delivery_window_start/end TIMESTAMPTZ NOT NULL`; `weight_kg NUMERIC(10,3) CHECK (>0)`; `priority`, `status`; `assigned_driver_id UUID` (logical Driver); `route_plan_id UUID` (logical Routing); `idempotency_key UUID`; timestamps, `version` | `UNIQUE(tenant_id,external_reference)` where non-null; `UNIQUE(tenant_id,idempotency_key)`; `(tenant_id,status,delivery_window_start)`; `(assigned_driver_id,status)` partial where assigned; GIST on geo points if PostGIS. Check window end > start. |
| `order_status_history` | `history_id UUID PK`; `order_id UUID NOT NULL FK orders`; `from_status`, `to_status`, `reason_code`, `actor_type`, `actor_id UUID` (logical), `occurred_at` | `(order_id,occurred_at DESC)`; append-only. |
| `outbox_events` | `event_id UUID PK`; `aggregate_type`; `aggregate_id UUID`; `event_type`; `schema_version SMALLINT`; `payload JSONB`; `occurred_at`; `published_at`; `attempt_count`; `locked_at`; `last_error` | partial `(occurred_at)` where `published_at IS NULL`; unique `(aggregate_id,event_type,event_id)`. Written atomically with aggregate change. |
| `processed_events` | same pattern as Auth | PK `(consumer_name,event_id)` for inbound route/driver events. |

`assigned_driver_id` and `route_plan_id` are denormalised references/snapshots, not a distributed join. Order validates a synchronous command only where user needs immediate feedback; later status changes are event-driven and version-checked.

### 3.3 Driver Service — `driver_db`

Chủ sở hữu: hồ sơ tài xế, phương tiện, khả năng làm việc và ca trực. Driver có `tenant_id` logical reference; không chia sẻ bảng `users` với Auth.

| Bảng | Cột chính (kiểu, ràng buộc) | Index/quan hệ nội bộ |
|---|---|---|
| `drivers` | `driver_id UUID PK`; `tenant_id UUID NOT NULL`; `user_id UUID` (logical Auth, nullable cho contractor); `full_name VARCHAR(255) NOT NULL`; `phone VARCHAR(20) NOT NULL`; `driver_type VARCHAR(20) CHECK`; `status VARCHAR(20) CHECK`; timestamps, `version` | `UNIQUE(tenant_id,phone)`; `UNIQUE(user_id)` where non-null; `(tenant_id,status)`. |
| `vehicles` | `vehicle_id UUID PK`; `driver_id UUID NOT NULL FK drivers`; `plate_number VARCHAR(20) NOT NULL`; `vehicle_type VARCHAR(30) NOT NULL`; `capacity_kg NUMERIC(10,3) CHECK (>0)`; `active BOOLEAN`; timestamps | `UNIQUE(plate_number)`; `(driver_id,active)` partial. Một tài xế có thể có lịch sử nhiều xe. |
| `driver_shifts` | `shift_id UUID PK`; `driver_id UUID NOT NULL FK drivers`; `start_at`, `end_at TIMESTAMPTZ`; `availability VARCHAR(20)`; `home_point geography`; timestamps, version | `(driver_id,start_at)`; check end > start; exclusion constraint chống overlap nếu PostgreSQL range dùng được. |
| `driver_availability_projection` | `driver_id UUID PK`; `tenant_id`; `availability`; `current_route_plan_id UUID` logical; `updated_at`; `source_event_id UUID` | `(tenant_id,availability)`; projection rebuild được. |
| `outbox_events`, `processed_events` | chuẩn như Order | Sự kiện `driver.*`; consumer idempotent. |

GPS tần suất cao không nằm ở PostgreSQL transaction table mặc định: ghi latest point vào Redis (`driver:{id}:location`, TTL 2–5 phút), stream sang kho telemetry/object storage nếu cần history. Chỉ snapshot vị trí dùng cho plan được lưu trong Routing.

### 3.4 Routing Engine Service — `routing_db`

Chủ sở hữu: input snapshot, execution của thuật toán, route plan và quyết định tối ưu. Routing không mutate order/driver source data. Nó nhận snapshot qua API/event và phát kết quả.

| Bảng | Cột chính (kiểu, ràng buộc) | Index/quan hệ nội bộ |
|---|---|---|
| `optimization_jobs` | `job_id UUID PK`; `tenant_id UUID`; `request_id UUID NOT NULL`; `trigger_type`; `algorithm_version`; `input_hash CHAR(64)`; `status`; `requested_at`, `started_at`, `completed_at`; `metrics JSONB`; `error_code` | `UNIQUE(tenant_id,request_id)`; `(status,requested_at)`; `(tenant_id,requested_at DESC)`. |
| `route_plans` | `route_plan_id UUID PK`; `job_id UUID NOT NULL FK optimization_jobs`; `tenant_id`; `status`; `plan_version INT`; `objective_cost NUMERIC(14,4)`; `total_distance_m NUMERIC(14,2)`; `estimated_duration_s INT`; `input_snapshot JSONB`; timestamps | `UNIQUE(job_id,plan_version)`; `(tenant_id,status,created_at DESC)`. Input snapshot là bằng chứng tái lập, không phải distributed FK. |
| `route_stops` | `stop_id UUID PK`; `route_plan_id UUID NOT NULL FK route_plans`; `driver_id UUID NOT NULL` logical; `order_id UUID` logical; `sequence_no INT NOT NULL`; `stop_type`; `location geography`; `eta_at`; `service_seconds`; `constraint_snapshot JSONB` | `UNIQUE(route_plan_id,driver_id,sequence_no)`; `UNIQUE(route_plan_id,order_id)` where `order_id IS NOT NULL`; `(driver_id,eta_at)`. |
| `model_registry` | `model_id UUID PK`; `model_type`; `version VARCHAR(100) UNIQUE`; `artifact_uri`; `metrics JSONB`; `status`; `trained_at`; `activated_at` | `(model_type,status)`. Binary model nằm object storage, không trong DB. |
| `outbox_events`, `processed_events` | chuẩn như Order | phát `route.plan.created`, `route.plan.failed`; xử lý `order.*`, `driver.*`. |

### 3.5 Notification Service — `notification_db`

Chủ sở hữu: delivery của thông báo, preference và audit gửi. Notification chỉ giữ recipient reference logical, không sao chép profile Auth.

| Bảng | Cột chính (kiểu, ràng buộc) | Index/quan hệ nội bộ |
|---|---|---|
| `notification_preferences` | `user_id UUID PK` (logical Auth); `push_enabled`, `email_enabled`, `sms_enabled`; `quiet_hours JSONB`; `updated_at`, `version` | PK đủ cho lookup. |
| `device_tokens` | `device_token_id UUID PK`; `user_id UUID`; `platform`; `token_ciphertext BYTEA NOT NULL`; `token_hash CHAR(64) NOT NULL`; `active`; timestamps | `UNIQUE(token_hash)`; `(user_id,active)` partial. |
| `notification_deliveries` | `delivery_id UUID PK`; `event_id UUID NOT NULL`; `user_id UUID`; `channel`; `template_code`; `payload JSONB`; `status`; `provider_message_id`; attempts; timestamps | `UNIQUE(event_id,user_id,channel,template_code)`; `(status,next_attempt_at)` partial. |
| `processed_events` | chuẩn idempotency | consumer RabbitMQ. |

Notification phải redact payload, retry with backoff, dead-letter sau ngưỡng; retention delivery audit 90–180 ngày theo chính sách riêng.

### 3.6 API Gateway và Redis

API Gateway không sở hữu dữ liệu nghiệp vụ hay database. Redis dùng cho rate limiting, JWT deny-list ngắn hạn, idempotency response cache, driver presence/location và cache projection; đặt TTL cho mọi key. Redis mất dữ liệu không được làm mất đơn, plan hay audit.

## 4. Quan hệ liên service và nhất quán

| Producer event | Consumer | Tác dụng |
|---|---|---|
| `tenant.created.v1`, `tenant.status-changed.v1` (Order) | Auth, Driver | Tạo/cập nhật membership projection và cho phép provision tài xế. |
| `order.created.v1`, `order.ready-for-planning.v1`, `order.cancelled.v1` (Order) | Routing, Notification | Cập nhật candidate set; gửi xác nhận khách hàng. |
| `driver.availability-changed.v1`, `driver.shift-changed.v1` (Driver) | Routing | Cập nhật pool có thể lập kế hoạch. |
| `route.plan-created.v1` (Routing) | Order, Driver, Notification | Order áp dụng assignment nếu version/input snapshot còn hợp lệ; Driver cập nhật availability; gửi route. |
| `delivery.status-reported.v1` (Driver) | Order, Notification | Order chuyển trạng thái hợp lệ rồi phát `order.status-changed.v1`. |

Envelope tối thiểu: `event_id`, `event_type`, `schema_version`, `occurred_at`, `producer`, `correlation_id`, `causation_id`, `aggregate_id`, `tenant_id`, `payload`. Routing key có version, ví dụ `order.created.v1`. Event là immutable; thêm field theo hướng backward-compatible, dùng event type/version mới khi thay đổi nghĩa.

**Transactional outbox:** command thay đổi aggregate và insert `outbox_events` cùng một DB transaction. Publisher claim batch bằng `FOR UPDATE SKIP LOCKED`, publish RabbitMQ có publisher confirms, chỉ sau xác nhận mới đánh dấu `published_at`. Consumer lưu `event_id` trong `processed_events` trong cùng transaction với projection/update nên at-least-once vẫn idempotent. Sau retry có backoff, gửi DLQ và alert; replay phải có runbook. Không dùng distributed transaction/2PC.

Các workflow dài dùng saga choreography: tạo order -> `PENDING_PLANNING`; routing tạo plan -> Order kiểm tra `order_id`, `tenant_id`, order version/status và áp dụng atomically -> `PLANNED`; thất bại/stale -> `route.plan-rejected.v1`, Routing tạo lại job. API trả `202 Accepted` cùng `job_id` cho thao tác tối ưu bất đồng bộ; UI poll/read model hoặc WebSocket event. Synchronous APIs chỉ truy vấn hoặc xác thực command không thể chờ event.

## 5. Review thiết kế hiện có và các khoảng trống

| Mức độ | Phát hiện trong repository | Rủi ro | Hành động |
|---|---|---|---|
| Critical | `docker-compose.yml` chỉ có một `postgres`/`DB_NAME`; Auth, Order và Driver cùng URL DB. | Không có database-per-service, dễ cross-query và blast radius chung. | Tạo `auth_db`, `order_db`, `driver_db`, `routing_db`, `notification_db` và role riêng; tốt nhất 5 PostgreSQL database/container logic. |
| Critical | Auth và Order có `flyway.enabled: false`, `ddl-auto: update`. | Migration không phải source of truth; prod schema drift. | Bật Flyway, `ddl-auto: validate`, fail startup khi migration lỗi. |
| Critical | Migration Driver tạo `drivers(id, full_name,...)`, nhưng entity map `driver(driver_id, current_lat,...)`. | Service khởi động/validate thất bại hoặc schema sai. | Baseline/reconcile một migration mới, chọn schema mục 3.3; không sửa V1 đã triển khai. |
| High | `orders.tenant_id` là FK nội bộ hợp lý, nhưng mô hình chỉ có `TIME`, không ngày/múi giờ; coordinate/lượng hàng dùng `DOUBLE`. | Không biểu diễn delivery thực tế và sai số tính toán. | Dùng `TIMESTAMPTZ`, `NUMERIC`, checks; PostGIS nếu truy vấn không gian. |
| High | Outbox Order không có schema version, lock/retry metadata và publisher không có broker confirmation/claim. | Double publish, tranh chấp publisher, event evolution khó. | Mở rộng outbox và consumer inbox theo mục 4. |
| High | `users.tenant_id` đồng thời với `tenant_memberships`. | Nguồn quyền tenant mơ hồ, không hỗ trợ multi-tenant user đúng. | Deprecate/drop `users.tenant_id` sau backfill membership; memberships là nguồn sự thật. |
| Medium | Driver migration áp unique phone/plate toàn cục, không có vehicle/shift/history/location strategy. | Khó tái dùng số, không lập kế hoạch capacity/ca. | Tách `vehicles`, `driver_shifts`, projection availability và Redis location. |
| Medium | Notification, Routing chưa có persistence design; API Gateway không nên có DB. | Thiếu audit, retry, reproducibility của thuật toán. | Tạo schemas 3.4–3.5; giữ Gateway stateless. |
| Medium | Timestamp hỗn hợp `TIMESTAMP` và entity LocalDateTime. | Mơ hồ timezone/audit. | Chuẩn hoá `TIMESTAMPTZ` và Java `Instant/OffsetDateTime`. |

## 6. Kế hoạch migration và triển khai

Không chỉnh sửa migration Flyway đã áp dụng. Mỗi service có history table riêng trong **database riêng** (có thể giữ tên mặc định `flyway_schema_history`), migration được chạy bởi đúng service role trong CI/CD trước rollout app.

1. **Nền tảng (M0):** provision năm database/role, backup database hiện tại, bật `pgcrypto`, PostGIS chỉ trong `order_db`/`routing_db` nếu chọn geo type. Sửa Compose/Kubernetes secrets để mỗi service chỉ nhận DSN của mình; cấm network/database credential của service khác.
2. **Đóng băng drift (M1):** chụp schema production bằng `pg_dump --schema-only`; tạo Flyway baseline khớp trạng thái đang chạy; đặt `ddl-auto: validate` và `flyway.enabled: true` ở Auth/Order. Đây là release không đổi contract.
3. **Tách dữ liệu (M2):** migrate bảng theo owner từ shared DB bằng snapshot/copy có checksum: Auth (`users`, tokens, memberships), Order (`tenants`, orders, outbox), Driver (`drivers`). Trong cutover ngắn, dừng writer hoặc dual-write có reconciliation; chuyển DSN từng service, validate count/checksum, sau đó revoke quyền shared DB. Không dùng cross-DB FK sau cutover.
4. **Sửa domain schema (M3):** phát hành migration forward-only theo service: Auth V3 add `updated_at/version/family_id/processed_events`, backfill rồi remove legacy `users.tenant_id` ở release sau; Order V5 normalise window/precision/history/inbox and outbox metadata; Driver V2 reconcile existing table/entity rồi V3 vehicles/shifts/outbox/inbox; Routing V1 và Notification V1 tạo schema mới.
5. **Event reliability (M4):** declare durable exchange/queues/DLQ, add publisher confirms, inbox idempotency, correlation IDs, compatibility tests and replay tool. Chạy shadow consumer/projection trước khi route command tới nó.
6. **Scalability (M5):** partition `orders` và `order_status_history` theo `created_at` tháng khi volume biện minh; partition telemetry outside OLTP. Chỉ thêm read model/warehouse từ events (CDC/outbox), không mở quyền đọc production database giữa services. Theo dõi outbox lag, DLQ depth, consumer lag, migration duration và p95 indexes.

Mỗi migration cần `up` forward-only, test migration trên bản copy production, kiểm tra index concurrently khi bảng lớn (migration không transaction), metric/backfill checkpoint và rollback ứng dụng tương thích schema cũ-mới. Rollback dữ liệu ưu tiên migration bù (compensating) thay vì `down` destructive.

## 7. Definition of done

- Mỗi service khởi động với DSN/role riêng, Flyway enabled và `ddl-auto=validate`.
- Không có query, FK hoặc ORM association xuyên service; contract/API/event được versioned và contract-tested.
- Mọi command tạo thay đổi liên service có outbox; mọi consumer có inbox/idempotency/DLQ/replay.
- Tenant authorization thực thi ở service owner; mọi truy vấn tenant-scoped dùng index tenant-first và được kiểm thử isolation.
- Route plan có input snapshot + algorithm/model version đủ để tái lập kết quả; location realtime có TTL và không là source of truth giao dịch.
