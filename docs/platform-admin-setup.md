# Tạo tài khoản quản trị hệ thống SwiftRoute

## Hai cổng truy cập

| Người dùng | Đăng nhập | Sau đăng nhập |
|---|---|---|
| Khách thuê / thành viên doanh nghiệp | `http://localhost:3000/login` | `/organizations`, rồi workspace của tenant |
| Quản trị hệ thống | `http://localhost:3000/admin/login` | `/admin/tenant-verifications` |

Hai khu vực có layout/menu riêng và dùng chung Auth Service. `OWNER`, `TENANT_ADMIN`, `OPERATOR` không tự có quyền xét duyệt doanh nghiệp. `SYSTEM_ADMIN` là role dùng cho tài khoản quản trị mới; `ADMIN` toàn hệ thống cũ vẫn được nhận diện để tương thích.

## 1. Tạo user bằng Postman

Gửi `POST http://localhost:8000/api/auth/register` với `Content-Type: application/json`:

```json
{
  "email": "platform-admin@example.com",
  "password": "ThayBangMatKhauRieng123!",
  "full_name": "SwiftRoute System Admin",
  "phone_number": "0900000000",
  "address": "Ninh Kieu, Can Tho",
  "city": "Can Tho",
  "state": "Can Tho",
  "zip_code": "900000",
  "country": "VN"
}
```

Các trường địa chỉ được giữ theo DTO đăng ký hiện tại. API tạo user với role mặc định; gửi `role` trong JSON không cấp quyền quản trị. Dùng API để mật khẩu được băm, thay vì ghi mật khẩu thô vào DB. Kết quả đăng ký thành công: HTTP 201.

## 2. Cập nhật role trong đúng database

Kết nối **`auth_db`**, chạy:

```sql
UPDATE users
SET role = 'SYSTEM_ADMIN'
WHERE lower(email) = lower('platform-admin@example.com')
RETURNING user_id, email, role, status;
```

Xác nhận đúng một user được cập nhật và `status` là `ACTIVE`. Tài khoản này không cần `tenant_id` hay membership OWNER để xét duyệt. Không dùng `TENANT_ADMIN` cho người duyệt toàn hệ thống.

## 3. Đăng nhập và kiểm tra trên web

1. Nếu đang đăng nhập, đăng xuất rồi đăng nhập lại để nhận token/profile theo role mới.
2. Mở `http://localhost:3000/admin/login`, đăng nhập bằng tài khoản vừa tạo.
3. Trang đích phải là `/admin/tenant-verifications`; menu chỉ thuộc khu quản trị, không có tạo tổ chức, vận đơn, đội xe hay đổi workspace.
4. Với tài khoản khách thuê, mở `/admin/tenant-verifications` phải hiện không có quyền; việc gọi API xét duyệt cũng được backend kiểm tra quyền.
5. Dùng tài khoản khách thuê ở `/login` để tạo tổ chức/onboarding. Khi admin mở URL khách thuê, hệ thống đưa về khu quản trị.

Hai cổng đang dùng chung phiên trình duyệt. Dùng cửa sổ ẩn danh hoặc hai browser profile nếu cần thao tác đồng thời bằng hai tài khoản.

## Điều kiện chạy

Frontend đã tách route. Backend workflow đã được triển khai local trong task 019; API danh sách xét duyệt trả 200 cho admin. SMTP vẫn cần được cấu hình theo `tenant-onboarding-implementation.md` để gửi email thực tế. Task tách cổng không tự cập nhật role, thêm user, chạy migration hay restart container. Không có testcase tự động mới; người vận hành kiểm tra trực tiếp bằng các bước trên.

Kiểm tra build/static ngày 2026-11-04: `npm run build` thành công; ESLint các file thay đổi thành công. Chưa xác nhận thao tác UI end-to-end; người vận hành test trực tiếp sau khi tạo tài khoản và cập nhật backend cần thiết.
