# ERD các service

*Bản nháp 1.0 · 11/10/2026 · Dự án giao hàng SaaS (tên tạm SwiftRoute)*

Tài liệu mô tả cấu trúc dữ liệu của 9 thành phần: Auth, Order, Driver, Payment, Routing Engine, ML Training, Notification, Agent và nền tảng dữ liệu (BigQuery). Quan hệ thể hiện bằng mũi tên, không vẽ sơ đồ: A 1 ──▶ N B nghĩa là một A có nhiều B; 1 ──▶ 1 là một đối một; 1 ──▶ 0..1 là một A có không hoặc một B. PK là khoá chính, FK là khoá ngoại (chỉ trong cùng CSDL), UK là ràng buộc duy nhất. Tham chiếu giữa các service nằm ở mục 2. Cấu trúc đã gộp các yêu cầu đã bàn: cấu hình theo loại hình, onboarding tenant, thực đơn, tài xế cơ hữu và Pool, đề xuất đơn, chat, trợ lý AI, mô hình học máy và kho dữ liệu.

## 1. Nguyên tắc để dễ mở rộng

- Khoá chính dùng UUID, khuyến nghị UUIDv7 (sắp theo thời gian, ít phân mảnh chỉ mục hơn UUID ngẫu nhiên).
- Mọi bảng thuộc về doanh nghiệp đều có tenant\_id và chỉ mục bắt đầu bằng tenant\_id: vừa cô lập dữ liệu giữa các tenant, vừa là khoá để chia nhỏ khi cần.
- Bảng nghiệp vụ có created\_at, updated\_at (TIMESTAMPTZ, UTC) và cột version để khoá lạc quan khi nhiều instance cùng ghi.
- Không có khoá ngoại giữa các service; liên kết bằng cột \*\_id (tham chiếu logic), kiểm chứng ở tầng ứng dụng và đồng bộ qua sự kiện.
- Mọi service có bảng outbox\_events (ghi sự kiện cùng giao dịch với dữ liệu rồi mới phát) và processed\_events (consumer chống xử lý lặp).
- Bảng nhật ký tăng nhanh (driver\_locations, notifications, chat\_messages, order\_status\_history, optimization\_runs, agent\_decision\_logs) phân vùng theo tháng hoặc ngày; dữ liệu cũ chuyển sang lakehouse.
- Cấu hình hay thay đổi (hồ sơ loại hình, tham số mô hình) lưu JSONB kèm schema\_version để thêm tham số không phải đổi cấu trúc bảng; tham số hay đọc (trọng số, ràng buộc giao hàng) có cột riêng để lập chỉ mục.
- Giá, tên món và hồ sơ cấu hình được chụp (snapshot) vào đơn và kế hoạch để dữ liệu lịch sử không đổi theo.
- Trạng thái dùng VARCHAR kèm CHECK; bảng có tham chiếu dùng xoá mềm (status hoặc is\_active) thay vì xoá cứng. Tiền dùng DECIMAL(12,2), mã tiền tệ VND.
- Mọi thay đổi cấu trúc qua file migration có phiên bản (Flyway).

## 2. Tham chiếu logic giữa các service

| Cột | Nằm ở | Trỏ tới | Cách bảo đảm |
| --- | --- | --- | --- |
| users.tenant\_id, drivers.tenant\_id | Auth, Driver | tenants (Order) | Gán khi đăng ký doanh nghiệp; kiểm tra qua API hoặc sự kiện |
| orders.customer\_id, drivers.user\_id | Order, Driver | users (Auth) | Lấy từ token đã xác thực |
| orders.assigned\_driver\_id, orders.plan\_id | Order | drivers (Driver), delivery\_plans (Routing) | Routing Engine gọi API gán sau khi kiểm tra |
| plan\_routes.driver\_id, route\_stops.order\_id | Routing | drivers, orders | Dữ liệu chụp tại thời điểm lập kế hoạch |
| driver\_offers.order\_id, driver\_incident\_reports.order\_id | Driver | orders (Order) | Kiểm tra khi tạo bản ghi |
| payments.order\_id | Payment | orders (Order) | Order tạo yêu cầu thanh toán; Payment trả kết quả bằng sự kiện |
| chat\_conversations (order, customer, driver) | Notification | Order, Auth, Driver | Tạo khi đơn chuyển PLANNED |
| agent\_\* (related\_order\_id, related\_driver\_id) | Agent | Order, Driver | Chỉ để truy vết |
| optimization\_runs.model\_id | Routing | ml\_models (ML Training) | Ghi phiên bản mô hình đã dùng |
| Kho dữ liệu | BigQuery | Mọi service | Không tham chiếu; nạp bản sao đã ẩn danh |

## 3. Bảng dùng chung mọi service

| Bảng | Cột chính | Mục đích |
| --- | --- | --- |
| outbox\_events | event\_id, aggregate\_type, aggregate\_id, event\_type, payload (JSONB), tenant\_id, created\_at, published\_at | Ghi sự kiện cùng giao dịch với dữ liệu, một tiến trình riêng đọc và phát lên message broker |
| processed\_events | event\_id, consumer, processed\_at | Mỗi consumer ghi nhận sự kiện đã xử lý để nhận lặp không tạo dữ liệu trùng |

## 4. Auth Service (auth\_db)

```
users 1 ──▶ N refresh_tokens
users 1 ──▶ N email_verifications
users 1 ──▶ N user_consents
users 1 ──▶ N staff_invitations (invited_by)
refresh_tokens 1 ──▶ 0..1 refresh_tokens (replaced_by_token_id)
login_attempts: không nối khoá ngoại
```

Cột chính:

- users: user\_id (uuid, PK), email (varchar 255, UK), phone (varchar 20), password\_hash (varchar 255), full\_name (varchar 255), role (TENANT\_ADMIN, OPS\_STAFF, DRIVER, CUSTOMER, SYSTEM\_ADMIN), tenant\_id (uuid, rỗng với khách hàng và tài xế Pool), status (ACTIVE, LOCKED), email\_verified (boolean), last\_login\_at (timestamptz), created\_at và updated\_at (timestamptz), version (int)
- refresh\_tokens: token\_id (uuid, PK), user\_id (uuid, FK), token\_hash (varchar 64, UK, SHA-256), issued\_at và expires\_at (timestamptz), revoked (boolean), replaced\_by\_token\_id (uuid, FK), device\_info (varchar 255), ip\_address (varchar 45)
- email\_verifications: verification\_id (uuid, PK), user\_id (uuid, FK), purpose (EMAIL\_VERIFY, PASSWORD\_RESET), code\_hash (varchar 64), attempts (int), expires\_at, consumed\_at và created\_at (timestamptz)
- user\_consents: consent\_id (uuid, PK), user\_id (uuid, FK), terms\_version (varchar 20), accepted\_at (timestamptz), ip\_address (varchar 45)
- staff\_invitations: invitation\_id (uuid, PK), tenant\_id (uuid), invited\_by (uuid, FK), email (varchar 255), role (OPS\_STAFF), token\_hash (varchar 64, UK), status (PENDING, ACCEPTED, EXPIRED, REVOKED), expires\_at và created\_at (timestamptz)
- login\_attempts: attempt\_id (uuid, PK), email (varchar 255), ip\_address (varchar 45), success (boolean), created\_at (timestamptz)

* email\_verifications phục vụ cả xác thực email (OTP 6 số, 10 phút) và quên mật khẩu, phân biệt bằng purpose; giới hạn gửi lại tính bằng số bản ghi trong 1 giờ.
* login\_attempts không nối khoá ngoại (cả email không tồn tại cũng được ghi) để giới hạn tần suất và chống dò; phân vùng theo ngày và xoá sau vài tuần.
* Chỉ mục: refresh\_tokens(user\_id, revoked), users(tenant\_id, role).

## 5. Order Service (order\_db)

```
tenants 1 ──▶ 1 tenant_profiles
business_profile_templates 1 ──▶ N tenant_profiles
tenants 1 ──▶ N tenant_upgrade_requests
tenants 1 ──▶ N onboarding_progress
tenants 1 ──▶ N pickup_points
tenants 1 ──▶ N products
tenants 1 ──▶ N orders
pickup_points 1 ──▶ N orders
orders 1 ──▶ N order_items (ít nhất 1)
products 1 ──▶ N order_items (product_id có thể rỗng)
orders 1 ──▶ N order_status_history
orders 1 ──▶ 0..1 order_reviews
```

Cột chính:

- tenants: tenant\_id (uuid, PK), business\_name (varchar 255), business\_type (RESTAURANT, CAFE, PHARMACY, RETAIL, OTHER), status (PENDING\_VERIFICATION, TRIAL, PENDING\_REVIEW, ACTIVE, SUSPENDED, EXPIRED), subscription\_plan (varchar 50), contact\_email (varchar 255), contact\_phone (varchar 20), address (varchar 500), trial\_expires\_at (timestamptz), terms\_version (varchar 20), terms\_accepted\_at (timestamptz), created\_at và updated\_at (timestamptz), version (int)
- business\_profile\_templates: template\_id (uuid, PK), business\_type (varchar 30, UK), name (varchar 100), params (jsonb), schema\_version (int), is\_active (boolean), updated\_at (timestamptz)
- tenant\_profiles: tenant\_id (uuid, PK, FK), template\_id (uuid, FK), time\_weight và cost\_weight (decimal 3,2, tổng bằng 1), max\_orders\_per\_trip (int), max\_delivery\_minutes (int), allow\_pooled\_drivers (boolean), overrides (jsonb), schema\_version (int), updated\_at (timestamptz), version (int)
- tenant\_upgrade\_requests: request\_id (uuid, PK), tenant\_id (uuid, FK), tax\_code (varchar 20), business\_address (varchar 500), license\_url (varchar 500), status (PENDING, APPROVED, REJECTED), reviewed\_by (uuid), review\_note (text), submitted\_at và reviewed\_at (timestamptz)
- onboarding\_progress: tenant\_id (uuid, FK), step\_code (varchar 50), completed\_at (timestamptz); khoá ghép tenant\_id và step\_code
- pickup\_points: pickup\_point\_id (uuid, PK), tenant\_id (uuid, FK), name (varchar 255), address (varchar 500), lat và lng (double), is\_default và is\_active (boolean)
- products: product\_id (uuid, PK), tenant\_id (uuid, FK), name (varchar 255), price (decimal 12,2), category (varchar 100), is\_active (boolean), created\_at và updated\_at (timestamptz)
- orders: order\_id (uuid, PK), tenant\_id (uuid, FK), customer\_id (uuid), pickup\_point\_id (uuid, FK), delivery\_lat và delivery\_lng (double), delivery\_address (varchar 500), window\_start và window\_end (timestamptz), weight\_kg (double), subtotal\_amount, delivery\_fee và total\_amount (decimal 12,2), priority (NORMAL, HIGH, URGENT), status (PENDING\_PAYMENT, PAYMENT\_FAILED, PAID, PLANNED, IN\_TRANSIT, DELIVERED, FAILED, CANCELLED), shift\_date (date), assigned\_driver\_id và plan\_id (uuid), created\_at và updated\_at (timestamptz), version (int)
- order\_items: order\_item\_id (uuid, PK), order\_id (uuid, FK), product\_id (uuid, FK, có thể rỗng), item\_name (varchar 255), quantity (int, lớn hơn 0), unit\_price và subtotal (decimal 12,2), note (varchar 255)
- order\_status\_history: history\_id (uuid, PK), order\_id (uuid, FK), from\_status và to\_status (varchar 30), changed\_by (uuid), reason (varchar 255), changed\_at (timestamptz)
- order\_reviews: review\_id (uuid, PK), order\_id (uuid, FK, UK), customer\_id và driver\_id (uuid), rating (int, từ 1 đến 5), comment (text), created\_at (timestamptz)

* tenant\_profiles chép các tham số từ business\_profile\_templates khi chọn loại hình, tenant ghi đè trong overrides hoặc các cột riêng; Routing Engine đọc trực tiếp các cột riêng.
* onboarding\_progress có khoá ghép (tenant\_id, step\_code) để tính phần trăm checklist.
* orders phân vùng theo tháng của created\_at; chỉ mục (tenant\_id, status, shift\_date) và (tenant\_id, customer\_id, created\_at). order\_status\_history phân vùng theo tháng.
* order\_items giữ item\_name và unit\_price đã chụp; product\_id cho phép rỗng khi món ngoài thực đơn.
* order\_reviews có ràng buộc duy nhất theo order\_id (mỗi đơn một đánh giá).

## 6. Driver Service (driver\_db)

```
drivers 1 ──▶ N driver_availability
drivers 1 ──▶ N driver_incident_reports
drivers 1 ──▶ N driver_unable_reports
drivers 1 ──▶ N driver_offers
drivers 1 ──▶ N driver_locations
```

Cột chính:

- drivers: driver\_id (uuid, PK), user\_id (uuid, UK), driver\_type (DEDICATED, POOLED), tenant\_id (uuid, rỗng với Pool), approval\_status (PENDING, APPROVED, REJECTED), approved\_by (uuid), approved\_at (timestamptz), vehicle\_type (MOTORBIKE, CAR, VAN), vehicle\_plate (varchar 20), capacity\_kg (double), status (OFFLINE, AVAILABLE, BUSY), current\_lat và current\_lng (double), last\_location\_at (timestamptz), rating\_avg (decimal 3,2), rating\_count (int), created\_at và updated\_at (timestamptz), version (int)
- driver\_availability: availability\_id (uuid, PK), driver\_id (uuid, FK), shift\_date (date), is\_available (boolean), reason (varchar 255); duy nhất theo (driver\_id, shift\_date)
- driver\_incident\_reports: report\_id (uuid, PK), driver\_id (uuid, FK), order\_id và tenant\_id (uuid), incident\_type (TRAFFIC\_DELAY, CUSTOMER\_UNAVAILABLE, VEHICLE\_PROBLEM, WRONG\_ADDRESS, OTHER), note (text), reported\_at (timestamptz)
- driver\_unable\_reports: report\_id (uuid, PK), driver\_id (uuid, FK), order\_id và tenant\_id (uuid), reason (VEHICLE\_PROBLEM, SHIFT\_ENDED, OVERLOADED, OTHER), note (text), reported\_at (timestamptz)
- driver\_offers: offer\_id (uuid, PK), driver\_id (uuid, FK), order\_id và tenant\_id (uuid), status (PENDING, ACCEPTED, REJECTED, EXPIRED), offered\_at, expires\_at và responded\_at (timestamptz)
- driver\_locations: location\_id (bigint, PK), driver\_id (uuid, FK), lat và lng (double), speed\_kmh (double), recorded\_at (timestamptz)

* tenant\_id của drivers rỗng với tài xế Pool; ở các bảng sự cố và đề xuất, tenant\_id là doanh nghiệp của đơn để báo cáo theo tenant mà không cần nối sang service khác.
* driver\_availability có ràng buộc duy nhất (driver\_id, shift\_date).
* driver\_locations là lịch sử vị trí (tuỳ chọn), phân vùng theo ngày, chỉ mục (driver\_id, recorded\_at); dùng cho ETA và phân tích. Vị trí mới nhất vẫn nằm ở drivers để truy vấn nhanh.
* Chỉ mục drivers(tenant\_id, status, approval\_status) phục vụ Routing Engine lấy tài xế sẵn sàng; driver\_offers(driver\_id, status) và (status, expires\_at) cho job hết hạn.

## 7. Payment Service (payment\_db)

```
payments 1 ──▶ N payment_webhook_logs
driver_payout_estimates: không nối khoá ngoại (liên kết logic theo order_id, driver_id)
```

Cột chính:

- payments: payment\_id (uuid, PK), order\_id, tenant\_id và customer\_id (uuid), amount (decimal 12,2), currency (varchar 3, mặc định VND), gateway (VNPAY), txn\_ref (varchar 64, UK), gateway\_txn\_id (varchar 64), status (PENDING, SUCCESS, FAILED, EXPIRED), mode (SANDBOX, MOCK), payment\_url (text), created\_at, paid\_at và expires\_at (timestamptz), version (int)
- payment\_webhook\_logs: log\_id (uuid, PK), payment\_id (uuid, FK, có thể rỗng), gateway (varchar 20), raw\_payload (jsonb), signature\_valid (boolean), received\_at (timestamptz)
- driver\_payout\_estimates: estimate\_id (uuid, PK), order\_id, driver\_id và tenant\_id (uuid), base\_fee, distance\_fee, peak\_fee, commission và estimated\_amount (decimal 12,2), created\_at (timestamptz)

Ghi chú mở rộng:

- Một đơn có thể có nhiều lần thanh toán (thử lại), nên quan hệ đơn với payments là 1 ──▶ N ở mức logic; txn\_ref là mã gửi cổng và duy nhất.
- Chỉ mục payments(tenant\_id, status, created\_at) và (status, expires\_at) cho job đóng giao dịch quá hạn; payment\_webhook\_logs phân vùng theo tháng.
- driver\_payout\_estimates chỉ ước tính, không phát sinh chi trả thật.

## 8. Routing Engine (routing\_db)

```
delivery_plans 1 ──▶ N plan_routes
plan_routes 1 ──▶ N route_stops
delivery_plans 1 ──▶ N unassigned_orders
delivery_plans 1 ──▶ N optimization_runs
benchmark_experiments 1 ──▶ N optimization_runs
delivery_plans 1 ──▶ N delivery_plans (parent_plan_id: kế hoạch giả lập trỏ về kế hoạch gốc)
```

Cột chính:

- delivery\_plans: plan\_id (uuid, PK), tenant\_id (uuid), shift\_date (date), status (DRAFT, APPROVED, PUBLISHED, CANCELLED), is\_simulation (boolean), parent\_plan\_id (uuid, FK), algorithm\_used (varchar 50), profile\_snapshot (jsonb), model\_id (uuid), total\_orders, assigned\_orders và unassigned\_orders (int), total\_distance\_km (double), total\_duration\_min (int), cost\_estimate (decimal 12,2), execution\_ms (int), created\_by (uuid), created\_at và published\_at (timestamptz)
- plan\_routes: route\_id (uuid, PK), plan\_id (uuid, FK), tenant\_id và driver\_id (uuid), driver\_type (DEDICATED, POOLED), total\_distance\_km (double), total\_duration\_min (int), total\_load\_kg (double), status (varchar 20)
- route\_stops: stop\_id (uuid, PK), route\_id (uuid, FK), order\_id (uuid), sequence\_no (int), planned\_eta (timestamptz), distance\_from\_prev\_km (double), status (PENDING, DELIVERED, FAILED), actual\_at (timestamptz), explain\_code (varchar 50)
- unassigned\_orders: plan\_id (uuid, FK), order\_id (uuid), reason\_code (OVER\_CAPACITY, TIME\_WINDOW, NO\_DRIVER, OTHER), detail (text); khoá ghép plan\_id và order\_id
- optimization\_runs: run\_id (uuid, PK), plan\_id (uuid, FK, có thể rỗng), experiment\_id (uuid, FK, có thể rỗng), tenant\_id (uuid), algorithm (varchar 50), n\_orders và n\_drivers (int), execution\_ms (int), states\_explored (bigint), objective\_value (double), optimality\_gap\_pct (double), model\_id (uuid), created\_at (timestamptz)
- benchmark\_experiments: experiment\_id (uuid, PK), name (varchar 255), dataset\_ref (varchar 255), note (text), created\_by (uuid), created\_at (timestamptz)

Ghi chú mở rộng:

- profile\_snapshot chụp hồ sơ cấu hình tại lúc chạy; kế hoạch giả lập (what-if) đặt is\_simulation = true và parent\_plan\_id để so sánh với kế hoạch gốc mà không ghi đè.
- optimization\_runs phục vụ so sánh thuật toán (DP thuần, DP + ML, Nearest Neighbor, Genetic Algorithm) qua experiment\_id và là nguồn cho fact\_route\_runs.
- Chỉ mục delivery\_plans(tenant\_id, shift\_date, status), route\_stops(route\_id, sequence\_no) và route\_stops(order\_id); route\_stops phân vùng theo tháng của kế hoạch.

## 9. ML Training Service (ml\_db)

```
ml_datasets 1 ──▶ N ml_models
ml_datasets 1 ──▶ N ml_training_runs
ml_models 1 ──▶ N ml_training_runs
```

Cột chính:

- ml\_datasets: dataset\_id (uuid, PK), version (varchar 20, UK), range\_start và range\_end (date), row\_count (bigint), storage\_uri (varchar 500), checksum (varchar 64), created\_at (timestamptz)
- ml\_models: model\_id (uuid, PK), name (varchar 100), version (varchar 20), framework (varchar 50), artifact\_uri (varchar 500), dataset\_id (uuid, FK), metrics (jsonb), is\_active (boolean), created\_by (uuid), trained\_at (timestamptz)
- ml\_training\_runs: training\_id (uuid, PK), model\_id (uuid, FK), dataset\_id (uuid, FK), params (jsonb), status (RUNNING, SUCCEEDED, FAILED), started\_at và finished\_at (timestamptz), metrics (jsonb)

Ghi chú mở rộng:

- Chỉ một mô hình is\_active cho mỗi name tại một thời điểm (chỉ mục duy nhất có điều kiện); quay lui chỉ đổi cờ is\_active.
- checksum của dataset giúp tái lập huấn luyện; file mô hình và dataset nằm ở kho lưu trữ, bảng chỉ giữ đường dẫn.

## 10. Notification Service (notification\_db)

```
chat_conversations 1 ──▶ N chat_messages
notifications, device_tokens, email_logs: không nối khoá ngoại
```

Cột chính:

- notifications: notification\_id (uuid, PK), user\_id và tenant\_id (uuid), type (varchar 50), title (varchar 255), body (text), data (jsonb), channel (PUSH, IN\_APP), status (PENDING, SENT, FAILED), created\_at, sent\_at và read\_at (timestamptz)
- device\_tokens: device\_token\_id (uuid, PK), user\_id (uuid), platform (ANDROID, IOS), fcm\_token (varchar 255, UK), is\_active (boolean), last\_seen\_at (timestamptz)
- email\_logs: email\_id (uuid, PK), to\_email (varchar 255), template (varchar 50), status (PENDING, SENT, FAILED), provider\_message\_id (varchar 100), error (text), created\_at và sent\_at (timestamptz)
- chat\_conversations: conversation\_id (uuid, PK), order\_id (uuid, UK), tenant\_id, customer\_id và driver\_id (uuid), status (ACTIVE, CLOSED), created\_at và closed\_at (timestamptz)
- chat\_messages: message\_id (uuid, PK), conversation\_id (uuid, FK), sender\_id (uuid), content (text), sent\_at và read\_at (timestamptz)

Ghi chú mở rộng:

- device\_tokens cho phép một người dùng có nhiều thiết bị nhận thông báo đẩy.
- notifications và chat\_messages phân vùng theo tháng; chỉ mục notifications(user\_id, read\_at, created\_at) và chat\_messages(conversation\_id, sent\_at).

## 11. Agent Service (agent\_db)

```
agent_conversations 1 ──▶ N agent_messages
agent_decision_logs 1 ──▶ N watchdog_alerts
```

Cột chính:

- agent\_conversations: conversation\_id (uuid, PK), agent\_type (PRODUCT\_RECOMMENDATION, BUSINESS\_ADVISOR), tenant\_id và user\_id (uuid), status (ACTIVE, CLOSED), created\_at và updated\_at (timestamptz)
- agent\_messages: message\_id (uuid, PK), conversation\_id (uuid, FK), role (USER, ASSISTANT), content (text), suggested\_product\_ids (jsonb), token\_usage (jsonb), created\_at (timestamptz)
- agent\_decision\_logs: decision\_id (uuid, PK), agent\_type (EXCEPTION\_HANDLING, OPS\_WATCHDOG, PRODUCT\_RECOMMENDATION, BUSINESS\_ADVISOR), tenant\_id, related\_order\_id và related\_driver\_id (uuid), input và output (jsonb), outcome (ACCEPTED, REJECTED, ESCALATED), latency\_ms (int), created\_at (timestamptz)
- watchdog\_alerts: alert\_id (uuid, PK), tenant\_id (uuid, có thể rỗng), alert\_type (varchar 50), severity (LOW, MEDIUM, HIGH), decision\_id (uuid, FK), status (OPEN, ACKNOWLEDGED, RESOLVED), resolved\_by (uuid), created\_at và resolved\_at (timestamptz)

Ghi chú mở rộng:

- token\_usage dùng theo dõi chi phí gọi mô hình ngôn ngữ; input và output của nhật ký không chứa dữ liệu cá nhân thô.
- agent\_decision\_logs phân vùng theo tháng, chỉ mục (tenant\_id, created\_at).

## 12. Nền tảng dữ liệu (BigQuery, star schema)

```
dim_date 1 ──▶ N fact_orders, fact_deliveries, fact_route_runs, fact_incidents
dim_tenant 1 ──▶ N fact_orders, fact_deliveries, fact_route_runs, fact_incidents
dim_business_type 1 ──▶ N dim_tenant
dim_driver 1 ──▶ N fact_deliveries, fact_incidents
dim_area 1 ──▶ N fact_orders, fact_deliveries
dim_algorithm 1 ──▶ N fact_route_runs
fact_orders 1 ──▶ 0..1 fact_deliveries
pipeline_runs: bảng theo dõi các lần nạp, không nối khoá
```

Cột chính:

- dim\_date: date\_key (int, PK, dạng yyyymmdd), date (date), day\_of\_week, week, month và year (int), is\_weekend (boolean)
- dim\_business\_type: business\_type\_key (int, PK), code (string), name (string)
- dim\_tenant (SCD loại 2): tenant\_key (int, PK), tenant\_hash (string), business\_type\_key (int, FK), plan (string), status (string), valid\_from và valid\_to (timestamp), is\_current (boolean)
- dim\_driver (SCD loại 2): driver\_key (int, PK), driver\_hash (string), driver\_type và vehicle\_type (string), valid\_from và valid\_to (timestamp), is\_current (boolean)
- dim\_area: area\_key (int, PK), ward, district và city (string)
- dim\_algorithm: algorithm\_key (int, PK), name và version (string)
- fact\_orders: order\_key (int, PK), date\_key, tenant\_key và area\_key (int, FK), customer\_hash (string), status (string), items\_count (int), delivery\_fee và total\_amount (numeric), created\_ts và paid\_ts (timestamp)
- fact\_deliveries: delivery\_key (int, PK), order\_key, date\_key, tenant\_key, driver\_key và area\_key (int, FK), planned\_eta và actual\_delivered\_at (timestamp), delay\_minutes (int), on\_time\_flag (boolean), distance\_km (float), cost\_estimate (numeric)
- fact\_route\_runs: run\_key (int, PK), date\_key, tenant\_key và algorithm\_key (int, FK), n\_orders và n\_drivers (int), execution\_ms (int), states\_explored (int), optimality\_gap\_pct và total\_distance\_km (float)
- fact\_incidents: incident\_key (int, PK), date\_key, tenant\_key và driver\_key (int, FK), incident\_type (string)
- pipeline\_runs: batch\_id (string, PK), source (string), started\_at và finished\_at (timestamp), rows\_loaded (int), status (RUNNING, SUCCEEDED, FAILED)

Ghi chú mở rộng:

- Bảng fact phân vùng theo ngày và phân cụm theo tenant\_key để giảm chi phí truy vấn.
- Không có tên, số điện thoại hay địa chỉ chi tiết; mã khách hàng và tài xế đã băm có muối; dim\_area suy từ toạ độ ra quận.
- Nạp lại theo lô (batch\_id) để chạy lại không tạo dữ liệu trùng.

## 13. Tóm tắt phân vùng và chỉ mục

| Bảng | Cách chia | Chỉ mục chính |
| --- | --- | --- |
| orders | Tháng theo created\_at | (tenant\_id, status, shift\_date), (tenant\_id, customer\_id, created\_at) |
| order\_status\_history | Tháng | (order\_id, changed\_at) |
| driver\_locations | Ngày | (driver\_id, recorded\_at) |
| route\_stops | Tháng theo kế hoạch | (route\_id, sequence\_no), (order\_id) |
| optimization\_runs | Tháng | (tenant\_id, created\_at), (algorithm, created\_at) |
| payment\_webhook\_logs | Tháng | (payment\_id), (received\_at) |
| notifications | Tháng | (user\_id, read\_at, created\_at) |
| chat\_messages | Tháng | (conversation\_id, sent\_at) |
| agent\_decision\_logs | Tháng | (tenant\_id, created\_at) |
| Bảng fact trên BigQuery | Ngày | Cụm theo tenant\_key |
