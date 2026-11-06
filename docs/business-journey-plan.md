# Hành trình đăng ký và phê duyệt doanh nghiệp

## Phạm vi đã chốt

Tái sử dụng Auth Service cho user, email verification và membership; Order Service cho tenant, onboarding, điểm lấy hàng, xét duyệt và readiness; Driver Service cho năng lực đội xe; Notification Service cho email bất đồng bộ. Không thêm Tenant Service, billing, subscription entitlement hoặc Shared Pool giả lập.

## Luồng nghiệp vụ

1. User đăng ký và xác minh email.
2. User tạo doanh nghiệp bằng `Idempotency-Key`.
3. Order DB lưu tenant và trạng thái cấp workspace; Auth tạo ACTIVE OWNER membership bằng lời gọi nội bộ có retry.
4. Chỉ khi provisioning là `READY`, frontend mới switch vào workspace.
5. OWNER hoàn tất năm bước: hồ sơ doanh nghiệp, điểm lấy hàng, phương thức giao, cấu hình giao và gửi xét duyệt.
6. Mỗi lần gửi tạo snapshot bất biến. System Admin xem hàng đợi, mở chi tiết và quyết định bằng optimistic version.
7. Tenant `APPROVED` được dùng toàn bộ tính năng hiện có, nhưng mỗi hành động vẫn kiểm tra prerequisite server-side.
8. Thiết lập tài xế riêng diễn ra sau phê duyệt và chỉ cần tại thời điểm giao việc; tạo đơn không bị chặn chỉ vì chưa có tài xế.

## Database source

### Order DB V10

- `tenants`: `contact_phone`, `contact_email`, `country_code`, `delivery_mode`, `delivery_settings`, `settings_confirmed`, `mode_confirmed`.
- `tenant_registration_keys`: chống tạo trùng theo OWNER và idempotency key.
- `onboarding_progress`: trạng thái và các bước đã hoàn thành do backend tính.
- `tenant_verification_requests`: snapshot, reviewer, reason, version và lịch sử từng lần nộp.

### Order DB V11

- `tenant_provisioning`: trạng thái `PROVISIONING`, `RETRY_REQUIRED`, `READY`; số lần thử, lease retry, lỗi gần nhất và thời điểm hoàn tất.
- Index retry provisioning và index hàng đợi xét duyệt.

### Auth DB

- Email verification token dùng hash, một lần, có hạn.
- OWNER membership là ACTIVE độc lập với tenant approval.
- Global system role và tenant membership role không thay thế lẫn nhau.

## Quy tắc tương thích

- Giữ `/api/tenants` và alias `/api/v1/tenants`.
- Giữ subscription metadata nhưng không dùng để khóa tính năng.
- Không tạo foreign key xuyên database.
- `SHARED_POOL` được lưu cấu hình nhưng readiness trả `SHARED_NETWORK_UNAVAILABLE`.
- Tenant cũ phải được đối chiếu OWNER membership trước khi provisioning chuyển `READY`.

## Hướng dẫn áp dụng thủ công

1. Sao lưu restorable `auth_db`, `order_db` và `notification_db`.
2. Áp dụng migration source theo thứ tự hiện có; Order V10 trước V11.
3. Cấu hình cùng `TENANT_INTERNAL_KEY` cho Auth, Order, Driver và Routing.
4. Cấu hình `JWT_SECRET`, `WEB_APP_URL` và `SMTP_*` nhất quán.
5. Khởi động backend trước Web Admin; kiểm tra provisioning rồi mới vào onboarding.
6. Dùng tài khoản System Admin riêng tại `/admin/login` để xét duyệt.

## Trạng thái xác minh

Đợt task-020 chỉ chỉnh source. Theo chỉ dẫn người dùng, chưa chạy build, lint, test, browser, migration hoặc triển khai container. Các thay đổi cần được kiểm tra thủ công sau khi người dùng cho phép rollout.
