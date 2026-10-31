# BÁO CÁO TỔNG HỢP CÔNG VIỆC ĐÃ HOÀN THÀNH
**Dự án:** SwiftRoute
**Thời gian cập nhật:** 29/10/2026

---

## 1. Tinh chỉnh & Sửa lỗi Backend (Order Service)
* **API Tìm kiếm Tenant lân cận (`GET /api/tenants/nearby`):**
  * **Sửa lỗi chính tả & cú pháp SQL trong `TenantRepository`:**
    * Sửa từ khóa `LITMIT` thành `LIMIT`.
    * Đổi tên cột từ `latitude` / `longitude` thành đúng schema cơ sở dữ liệu: `pickup_lat` / `pickup_lng`.
    * Thêm điều kiện lọc chỉ lấy các đối tác đã duyệt hoạt động: `status = 'APPROVED'`.
  * **Sửa Controller & Service (`TenantController`, `TenantService`):**
    * Khắc phục lỗi compile do lệch kiểu tham số giữa Controller và Service.
    * Đưa tham số `lat`, `lng`, `limit` vào `@RequestParam` của endpoint `GET /api/tenants/nearby`.
    * Chuẩn hóa wrapper trả về theo định dạng chung `ApiResponse<List<TenantResponse>>` và bổ sung `@Operation` cho Swagger.
* **Cơ chế Phân trang (Pagination):**
  * Khảo sát hiện trạng các endpoint GET danh sách trong hệ thống.
  * Phân tích và giải thích chi tiết kiến trúc **Cursor-based pagination** (Opaque Base64 Cursor) so với **Offset-based pagination** theo tài liệu đặc tả `docs/specification/06-api-contracts.md`.

---

## 2. Khởi tạo & Xử lý Dữ liệu Mẫu (Seed Data)
* **Khắc phục các lỗi ràng buộc Database (PostgreSQL):**
  * Xử lý lỗi `NOT NULL` constraint trên các cột audit: `created_at`, `status_updated_at`, `version` khi chèn trực tiếp bằng SQL (bổ sung `now()` và `version = 0`).
  * Xử lý lỗi `check constraint tenants_subscription_plan_check`: chuẩn hóa gói đăng ký theo enum hợp lệ (`FREE_TRIAL`, `BASIC`, `PREMIUM`).
* **Tạo dữ liệu mẫu 7 Tenant tại TP. Hồ Chí Minh:**
  * Bổ sung các cửa hàng/kho hàng trải đều các quận: Quận 1, Quận 3, Quận 5, Quận 7, Quận 10, Tân Bình, Bình Thạnh.
  * Đầy đủ tọa độ GPS thực tế và nhóm ngành (`RESTAURANT`, `CAFE`, `RETAIL`, `PHARMACY`, `OTHER`) để phục vụ kiểm thử tính năng tính khoảng cách và tối ưu lộ trình.

---

## 3. Hoàn thiện Mobile App (Flutter)
* **Xây dựng Model hoàn chỉnh cho Tenant (`tenant_model.dart`):**
  * Loại bỏ `import 'dart:ffi';`, chuẩn hóa các kiểu dữ liệu từ `Double`/`Long` về kiểu gốc của Dart (`double`, `int`).
  * Bổ sung đầy đủ 3 enum hỗ trợ hiển thị UI tiếng Việt và ánh xạ backend:
    * `BusinessType` (`restaurant`, `cafe`, `retail`, `pharmacy`, `other`).
    * `TenantStatus` (`pending`, `approved`, `rejected`, `suspended`).
    * `SubscriptionPlan` (`freeTrial`, `basic`, `premium`).
  * Map chính xác 100% với DTO `TenantResponse` từ backend (`snake_case`).
  * Thêm đầy đủ các hàm: `fromJson`, `toJson`, `copyWith`.
  * Đã chạy lệnh `flutter analyze` kiểm tra và xác nhận đạt chuẩn (`No issues found!`).

---

## 4. Cấu hình Hạ tầng & Gateway (API Gateway)
* **Định tuyến Routing Engine:**
  * Cập nhật file cấu hình `application.yml` của `api-gateway`.
  * Thêm route `/api/routing/**` trỏ tới `routing-engine:8084` kèm bộ lọc `StripPrefix=2` để kết nối thông suốt với engine tối ưu hóa lộ trình FastAPI.

---

## 5. Rà soát Tổng thể Codebase & Điều chỉnh Kế hoạch Demo
* **Audit toàn diện các dịch vụ:**
  * **Auth Service:** Đầy đủ đăng ký, đăng nhập, JWT, refresh token.
  * **Order Service:** Hoàn thiện tạo đơn, mock payment (`/pay`), gán lộ trình (`/assign-batch`), truy vấn đơn tài xế.
  * **Driver Service:** Đầy đủ 12 endpoints quản lý tài xế, cập nhật tọa độ GPS và sự kiện giao nhận.
  * **Routing Engine:** Đã triển khai cả 3 thuật toán TSP (Held-Karp Quy hoạch động, Nearest Neighbor, Genetic Algorithm).
* **Điều chỉnh phạm vi thực hiện theo yêu cầu mới:**
  * **Loại bỏ Web Admin** ra khỏi phạm vi demo để tối ưu hóa thời gian.
  * **Chuyển trọng tâm sang Mobile App:** Khách hàng (Đặt hàng + Mock Payment) $\rightarrow$ Điều phối (Gán đơn trực tiếp trên Mobile) $\rightarrow$ Tài xế (Nhận lộ trình, cập nhật trạng thái giao hàng `IN_TRANSIT` $\rightarrow$ `DELIVERED`).

