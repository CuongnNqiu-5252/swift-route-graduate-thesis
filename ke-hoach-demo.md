# Kế hoạch demo (thứ bảy)

Mục tiêu: chạy được một luồng từ đặt hàng đến giao xong. Phần còn lại làm giả hoặc cắt.

## 1. Luồng demo tối thiểu (khoảng 8 phút)

| Bước | Ai | Làm gì | Service |
|---|---|---|---|
| 1 | Quản trị viên | Đăng nhập, xem thực đơn đã có sẵn (thêm 1 món để minh hoạ) | Auth, Order |
| 2 | Khách hàng | Đăng nhập app, xem cửa hàng và thực đơn, tạo đơn | Auth, Order |
| 3 | Khách hàng | Thanh toán, đơn chuyển sang PAID | Payment |
| 4 | Nhân viên vận hành | Bấm lập kế hoạch cho 8 đến 10 đơn, xem lộ trình từng tài xế, duyệt | Routing Engine |
| 5 | Tài xế | Mở app, xem lộ trình, bấm đã giao | Routing, Order |
| 6 | Khách hàng | Thấy đơn chuyển DELIVERED | Order |
| 7 | Trình bày | Bảng so sánh thuật toán (DP thuần, DP + ML, Nearest Neighbor, Genetic Algorithm) | Kết quả chạy trước, không cần live |

## 2. Làm giả hoặc cắt

| Hạng mục | Cách xử lý |
|---|---|
| Đăng ký doanh nghiệp, OTP email | Tạo sẵn tenant bằng seed; có thể chỉ nói bằng lời |
| Thanh toán | Dùng chế độ giả lập nếu VNPay sandbox chưa xong; nếu xong rồi thì demo bằng thẻ thử |
| Theo dõi vị trí (SSE) | Màn hình khách tự làm mới (polling) mỗi vài giây là đủ |
| Chat, quên mật khẩu, tài xế Pool nhận đơn, what-if | Cắt, hoặc chỉ nêu trong hướng phát triển |
| Trợ lý AI | Chỉ làm nếu dư thời gian; nếu có thì chọn gợi ý món |
| Nền tảng dữ liệu GCP | Không đưa vào live demo; chiếu biểu đồ dựng sẵn |
| Thuật toán DP + ML | Lập kế hoạch trực tiếp bằng bản Naive nếu DP + ML chưa tích hợp; chiếu kết quả thực nghiệm DP + ML riêng |

## 3. Lịch bốn ngày còn lại (đóng băng tính năng từ thứ sáu)

| Ngày | Việc |
|---|---|
| Thứ tư | Nối xong luồng backend: Payment (giả lập cũng được), Routing Engine Naive, API tài xế; kiểm tra bằng Postman |
| Thứ năm | Giao diện tối thiểu: web nhân viên (danh sách đơn, nút lập kế hoạch, xem kết quả), app khách (thực đơn, đặt đơn, trạng thái), app tài xế (lộ trình, đã giao) |
| Thứ sáu | Viết script seed dữ liệu demo; chạy thử cả luồng 2 lần; quay video dự phòng |
| Thứ bảy sáng | Chạy thử lần cuối, không sửa code; chuẩn bị slide kết quả thuật toán |

Dữ liệu seed gồm: 1 tenant, 6 món, 1 khách, 1 nhân viên, 3 tài xế, 8 đến 10 đơn quanh khu vực demo.

## 4. Phòng sự cố

- Seed dữ liệu bằng một lệnh để chạy lại từ đầu được.
- Quay sẵn video toàn bộ luồng, dùng khi mạng hoặc dịch vụ lỗi.
- Chạy toàn bộ trên máy local bằng Docker Compose, không phụ thuộc cloud lúc demo.

## 5. Checklist sáng thứ bảy

- [ ] Docker Compose chạy sạch từ đầu, mọi service báo health OK
- [ ] Chạy lệnh seed, dữ liệu đúng như kịch bản
- [ ] Đi hết 6 bước demo một lần, không lỗi
- [ ] Video dự phòng mở được trên máy trình chiếu
- [ ] Slide kết quả thuật toán và sơ đồ kiến trúc sẵn sàng
- [ ] Pin, sạc, kết nối màn hình, điện thoại thử nghiệm đã cài app
