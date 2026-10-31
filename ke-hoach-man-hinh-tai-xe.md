# Kế hoạch màn hình tài xế sau đăng nhập (nối API backend)

Phạm vi: app Flutter (dio, Riverpod `@riverpod`, `AsyncNotifier`) cho vai trò DRIVER, từ lúc đăng nhập xong. Cấu trúc theo `CLAUDE.md`: `data / application / presentation`.

## 1. Màn hình và thứ tự làm

| Ưu tiên | Màn | Mục đích | Cần cho demo |
|---|---|---|---|
| 1 | D02 Trang chủ và trạng thái | Bật Available, gửi vị trí, xem điểm kế tiếp | Có |
| 2 | D05 Lộ trình trong ngày | Danh sách điểm giao theo thứ tự | Có |
| 3 | D06 Điểm giao hiện tại | Đã giao, giao thất bại, báo sự cố | Có |
| 4 | D09 Tổng kết ca | Số đơn, quãng đường, tỷ lệ đúng hạn | Nên có |
| 5 | D04 Đề xuất đơn (Pool) | Nhận hoặc từ chối | Tuỳ chọn |
| 6 | D07 Báo sự cố, D08 Không thể thực hiện đơn, D03 Lịch làm việc, D01 Hồ sơ | Phần phụ | Bỏ qua bằng seed |

Điều hướng: Route, Offers, Messages, Profile. Với demo chỉ cần tab Route (D02, D05, D06, D09) và Profile (đăng xuất).

## 2. Bảng API theo màn hình

Mọi phản hồi theo dạng `{ data, error, timestamp }`, JSON snake_case, gọi qua một Dio dùng chung có gắn access token.

| Màn | Việc | API | Ghi chú |
|---|---|---|---|
| Sau đăng nhập | Lấy user hiện tại | GET /api/v1/auth/me | Lấy user_id và role; nếu role khác DRIVER thì chặn |
| Sau đăng nhập | Lấy hồ sơ tài xế | GET /api/v1/drivers/me | **Chưa có trong hợp đồng**, xem mục 8 |
| D02 | Đổi trạng thái | PATCH /api/v1/drivers/{id}/status, body `{ status }` | OFFLINE, AVAILABLE, BUSY |
| D02 | Gửi vị trí | PATCH /api/v1/drivers/{id}/location, body `{ lat, lng }` | Trả 204, gọi định kỳ |
| D02 | Thống kê và điểm kế tiếp | GET /api/v1/routes/today?driver_id= | Dùng chung dữ liệu với D05 |
| D05 | Lộ trình trong ngày | GET /api/v1/routes/today?driver_id= | Xem hình dạng đề xuất ở mục 6 |
| D06 | Đã giao hoặc thất bại | POST /api/v1/route-stops/{stop_id}/complete, body `{ result, note }` | result là DELIVERED hoặc FAILED |
| D06, D07 | Báo sự cố | POST /api/v1/drivers/{id}/incidents, body `{ order_id, incident_type, note }` | TRAFFIC_DELAY, CUSTOMER_UNAVAILABLE, VEHICLE_PROBLEM, WRONG_ADDRESS, OTHER |
| D09 | Tổng kết ca | GET /api/v1/routes/summary?driver_id=&date= | |
| D04 | Xem đề xuất | GET /api/v1/drivers/{id}/offers?status=PENDING | Bổ sung theo kế hoạch Driver Service |
| D04 | Nhận hoặc từ chối | POST /api/v1/offers/{offerId}/respond, body `{ accept }` | |
| D08 | Không thể thực hiện | POST /api/v1/drivers/{id}/unable-reports | reason: VEHICLE_PROBLEM, SHIFT_ENDED, OVERLOADED, OTHER |
| D03 | Lịch làm việc | POST /api/v1/drivers/{id}/availability, body `{ shift_date, is_available, reason }` | |
| Mọi màn | Làm mới token | POST /api/v1/auth/refresh | Do interceptor xử lý |
| Profile | Đăng xuất | POST /api/v1/auth/logout | Xoá token khỏi secure storage |

## 3. Cấu trúc code

```
lib/core/network/      dio_client.dart, auth_interceptor.dart, api_response.dart
lib/features/driver/
  home/        data/ (driver_repository)   application/ (driver_profile, work_status, location_reporter)
               presentation/ (driver_home_screen, status_card, stat_row, next_stop_card)
  route/       data/ (route_repository)    application/ (today_route)
               presentation/ (route_screen, stop_card)
  stop/        application/ (stop_action)  presentation/ (stop_detail_screen, report_issue_sheet)
  summary/     application/ (shift_summary) presentation/ (summary_screen)
```

- Model dùng `json_serializable` với `fieldRename: FieldRename.snake`.
- `auth_interceptor`: gắn Bearer token; khi gặp 401 thì gọi refresh một lần rồi thử lại; refresh lỗi thì xoá token và về màn đăng nhập.
- Token lưu bằng `flutter_secure_storage`.

## 4. Provider và hành vi

| Provider | Việc | Hành vi chính |
|---|---|---|
| driverProfile | Nạp hồ sơ sau đăng nhập | Giữ driver_id, driver_type, approval_status, status |
| workStatus | Đổi trạng thái | Cập nhật lạc quan, lỗi thì hoàn lại và báo; nếu chưa được duyệt thì hiện 'Waiting for approval' và khoá công tắc |
| locationReporter | Gửi vị trí | Chỉ chạy khi AVAILABLE hoặc BUSY; luồng geolocator với distanceFilter khoảng 20 m, gửi tối đa mỗi 5 đến 10 giây; dừng khi OFFLINE hoặc thoát app; từ chối quyền thì hiện thông báo kèm nút mở cài đặt |
| todayRoute | Lộ trình hôm nay | Kéo để làm mới; tự làm mới khoảng 30 giây khi màn đang mở; rỗng thì hiện 'No route assigned yet' |
| stopAction | Hoàn thành điểm giao | Khoá nút khi đang gửi để tránh bấm đúp; thành công thì làm mới todayRoute và chuyển tới điểm kế tiếp |
| shiftSummary | Tổng kết ca | Nạp theo ngày, có thể chọn ngày |

## 5. Trạng thái giao diện cho mỗi màn

- Đang tải: khung xám đơn giản, không thêm thư viện shimmer.
- Lỗi: thông báo ngắn và nút Retry; lỗi 403 hoặc 409 hiện lý do từ trường `error.message`.
- Rỗng: D05 hiện 'No route assigned yet'; D09 hiện 'No data for this day'.
- Mất mạng: D05 hiển thị lộ trình đã tải lần gần nhất kèm dải báo 'Offline'; nút hoàn thành điểm giao tạm vô hiệu (đồng bộ lại khi có mạng là phần mở rộng).

## 6. Hình dạng dữ liệu đề xuất cho API lộ trình

Routing Engine chưa chốt hợp đồng, nên đề xuất thống nhất như sau để app và backend làm song song.

```json
GET /api/v1/routes/today?driver_id=...
{
  "data": {
    "route_id": "uuid", "plan_id": "uuid", "shift_date": "2026-10-17",
    "total_stops": 8, "total_distance_km": 18.4, "total_duration_min": 135,
    "stops": [
      { "stop_id": "uuid", "order_id": "uuid", "sequence_no": 1,
        "address": "123 Nguyen Hue, Q1", "lat": 10.7756, "lng": 106.7019,
        "customer_name": "Nguyen Van A", "planned_eta": "2026-10-17T01:15:00Z",
        "status": "PENDING", "weight_kg": 2.5, "items_summary": "2 x Ca phe sua da",
        "note": "Goi truoc khi toi", "explain_code": "CLOSEST_NEXT" }
    ]
  }
}

POST /api/v1/route-stops/{stop_id}/complete
{ "result": "DELIVERED", "note": null }
=> { "data": { "stop_id": "uuid", "status": "DELIVERED", "next_stop_id": "uuid" } }
```

Số điện thoại khách không đưa vào phản hồi ở giai đoạn này (quyền riêng tư); nút Call ẩn cho tới khi có cách che số.

## 7. Làm song song khi backend chưa xong

- Định nghĩa `abstract class RouteRepository` và `DriverRepository`; mỗi cái có bản thật (dùng Dio) và bản giả (trả JSON mẫu theo mục 6).
- Chọn bản giả hay thật bằng cách ghi đè provider (`ProviderScope.overrides`), không sửa code màn hình.
- Chuyển sang bản thật từng API một khi backend sẵn sàng, mỗi lần chạy lại luồng demo.

## 8. Chỗ hở cần xác nhận với backend

| Vấn đề | Đề xuất |
|---|---|
| Chưa có cách lấy hồ sơ tài xế từ user đang đăng nhập | Thêm GET /api/v1/drivers/me (hoặc lọc theo user_id) |
| Routing Engine chưa có API tài xế | Làm theo mục 6 ở ngày 5 của kế hoạch Routing Engine |
| Điểm giao FAILED có tính lại điểm kế tiếp không | Chốt: chỉ ghi trạng thái, không tính lại lộ trình |
| Múi giờ | Backend trả UTC, app chuyển sang giờ Việt Nam khi hiển thị |
| Tài xế chưa được duyệt bật AVAILABLE | Backend trả 409 kèm lý do, app hiển thị |

## 9. Kế hoạch theo ngày

| Ngày | Việc | Kết quả |
|---|---|---|
| 1 | Dio, interceptor refresh, ApiResponse, driverProfile; D02 (công tắc trạng thái, gửi vị trí) | Đăng nhập xong thấy trang chủ, bật được Available và vị trí lên server |
| 2 | D05 và D06 với repository giả, rồi nối API thật khi sẵn sàng; hoàn thành điểm giao | Đi hết một lộ trình mẫu |
| 3 | D09, trạng thái tải, lỗi, rỗng, offline cơ bản; unit test workStatus và stopAction; chạy thử luồng demo hai lần | Luồng tài xế ổn định cho demo |
| 4 (nếu dư) | D04, D07, D08, D03 | Phần phụ |

## 10. Kiểm thử và checklist

- Unit test: workStatus (cập nhật lạc quan, hoàn lại khi lỗi), stopAction (chặn bấm đúp), todayRoute (rỗng, lỗi).
- Widget test: StopCard với dữ liệu thiếu trường (ẩn đúng phần tử).
- Thủ công: đăng nhập, bật Available, thấy vị trí trong Driver Service, mở lộ trình, giao hết các điểm, xem tổng kết.
- Checklist: token hết hạn tự làm mới; từ chối quyền vị trí không làm app treo; thoát app dừng gửi vị trí; đăng xuất xoá token.
