# Kế hoạch phát triển Operation Flow

## Phần nên giữ ở cấp doanh nghiệp

| Cấu hình | Nơi quản lý | Vai trò trong Operation Flow |
|---|---|---|
| Trọng số thời gian và quãng đường | Cài đặt doanh nghiệp | Giá trị mặc định cho lập tuyến, chèn đơn và tối ưu lại. Luồng ưu tiên SLA có thể đặt giá trị riêng khi backend hỗ trợ. |
| Số đơn tối đa mỗi tài xế | Cài đặt doanh nghiệp | Ràng buộc mặc định cho node lập kế hoạch. |
| Ngưỡng gom đơn, thời gian chờ và tự động gom | Cài đặt doanh nghiệp trong giai đoạn chuyển tiếp | Giữ cho các lượt điều phối tự động hiện có. Khi một luồng tiếp quản cùng phạm vi, chuyển điều kiện sang luồng và ngừng nguồn kích hoạt cũ cho phạm vi đó. |
| Điểm lấy hàng và vùng giao | Màn hình Điểm lấy hàng và Vùng giao hàng | Node chọn các bản ghi theo ID trong cùng doanh nghiệp. Trình nhập đơn dùng điểm lấy hàng mặc định khi phù hợp. |

Điểm lấy hàng đầu tiên và các điểm thêm sau đều thuộc `pickup_locations`. Cờ `is_default` nằm trên bản ghi điểm lấy hàng. `tenants.pickup_lat/pickup_lng` vẫn hỗ trợ dữ liệu cũ trong tạo đơn, lập tuyến và kiểm tra sẵn sàng. Chỉ bỏ fallback sau khi kiểm kê, chuyển các tọa độ còn dùng sang bản ghi điểm lấy hàng và kiểm tra lại đơn cũ.

## 1. Hoàn tất lịch lập kế hoạch và theo dõi kết quả

- Triển khai đồng bộ V27, Order Service và Web Admin. Đối chiếu giờ, trạng thái bật/tắt và lịch kế tiếp của doanh nghiệp sau khi chuyển. Kiểm tra một lượt chạy tự động trên dữ liệu thử.
- Giữ thao tác **Lưu bản nháp**, **Kích hoạt** và **Tạm dừng**. Hiện cấu hình đang chạy và báo rõ khi bản nháp khác phiên bản đã kích hoạt.
- Thêm **Lịch sử chạy** với thời điểm, nguồn kích hoạt, phiên bản, kết quả và liên kết sang các tuyến được tạo. Phân biệt thành công, bỏ qua vì không có đơn hoặc tài xế, và lỗi kỹ thuật.
- Dùng định danh ổn định cho từng lượt giờ hoặc sự kiện. Bảo đảm nhiều worker và thao tác thử lại chỉ tạo một bộ kết quả cho cùng đầu vào. Giữ việc ghi kết quả và tác động nghiệp vụ trong cùng giao dịch khi cùng cơ sở dữ liệu.

**Điều kiện nghiệm thu** — Chỉnh bản nháp không làm đổi lịch đang chạy. Tạm dừng chặn lượt kế tiếp. Hai worker gặp cùng một lượt chỉ tạo một bộ kết quả. Điều phối viên tra được lý do bỏ qua.

## 2. Nhập đơn theo ca từ Google Drive hoặc Sheets

| Node | Cấu hình và đầu ra |
|---|---|
| **Lịch chạy** | Giờ Việt Nam và các ngày trong tuần. Hiện lần chạy kế tiếp. |
| **Đọc file/tab** | Kết nối Google theo doanh nghiệp, chọn file và tab cố định. Kết nối định kỳ dùng quyền truy cập nền, có thao tác kết nối lại khi hết quyền. |
| **Kiểm tra dữ liệu nhập** | Ánh xạ cột và trường bắt buộc. Trả số dòng hợp lệ, trùng mã nguồn, thiếu địa chỉ và lỗi. |
| **Nhập vận đơn** | Điểm lấy hàng mặc định hoặc ánh xạ theo dòng. Tái sử dụng hàng đợi Cần bổ sung địa chỉ và cơ chế chống trùng theo mã nguồn của doanh nghiệp. |
| **Thông báo kết quả** | Chọn người nhận theo vai trò và điều kiện gửi. Mở trực tiếp kết quả lần nhập hoặc hàng đợi cần sửa. |

**Điều kiện nghiệm thu** — Luồng đọc đúng tab khi trình duyệt đã đóng. Chạy lại cùng file không tạo đơn trùng. Dòng lỗi giữ được số dòng và lý do. Khi mất quyền Google, lần chạy hiện hướng xử lý và gửi thông báo đúng nhóm. Thiết kế lưu quyền Google nền và dữ liệu thực thi cần được chốt trước khi bổ sung schema.

## 3. Điều phối theo kho, ca và điều kiện

1. **Lấy đơn chờ điều phối** trả danh sách ID theo điểm lấy hàng, vùng giao, khung giờ, ưu tiên và giới hạn số đơn. ID được xác thực lại theo doanh nghiệp và trạng thái trước khi lập tuyến.
2. **Điều kiện** chia nhánh theo số đơn, tuổi đơn chờ lâu nhất hoặc số tài xế khả dụng. Điều phối viên chọn một trong các cách xử lý có sẵn, ví dụ tiếp tục, chờ lượt tới hoặc báo người phụ trách.
3. **Kiểm tra đội xe** lấy tài xế nội bộ đủ điều kiện theo ca, phương tiện và tải trọng. Trả nhóm đơn đủ khả năng xếp tuyến và nhóm thiếu tài xế.
4. **Lập kế hoạch** xử lý đúng tập đơn từ node trước. Trả các tuyến nháp cùng đơn bị loại và lý do. Chốt rõ cách chạy nhiều kho vì lịch hiện tại dùng một nhóm kho mỗi lượt, còn thao tác thủ công có thể xử lý nhiều nhóm.
5. **Giao tuyến** dùng vòng đời kế hoạch hiện có và giao tuyến sau khi lập kế hoạch thành công.

Trước khi cho đặt trọng số riêng theo node, sửa cách gRPC diễn giải trọng số bằng 0 và kiểm thử các cặp 0/100, 50/50, 100/0. Hiện giá trị bằng 0 bị thay bằng 0,5 trên đường truyền này.

**Điều kiện nghiệm thu** — Với đầu vào 18 đơn ở kho A, node lập kế hoạch chỉ có thể sử dụng 18 ID đó. Đơn đã được một lượt khác nhận thì bỏ qua và ghi lý do.

## 4. Ngoại lệ và chính sách thực thi

- Bổ sung **Khi có sự kiện** cho từ chối tuyến, quá hạn xác nhận, giao thất bại và chênh lệch COD. Giai đoạn đầu ưu tiên **Tạo việc cần xử lý** và **Thông báo** với liên kết vào đúng hồ sơ.
- Trước khi thêm node **Chờ chuẩn bị hàng**, xác định thời điểm sẵn sàng lấy hàng và cách tài xế, hàng đợi điều phối đọc trạng thái đó. Trước khi thêm **Yêu cầu bằng chứng giao hàng**, thống nhất loại bằng chứng theo đơn và kiểm tra ở API hoàn tất giao. Hàng nhạy cảm cần quy tắc cụ thể như giới hạn phương tiện hoặc cách bàn giao.
- Chạy thử bằng bản xem trước đầu vào và hành động dự kiến. Hiện bảng chọn node từ nút **+**, chỉ gồm node phù hợp với loại dữ liệu của bước trước. Các mẫu đầu tiên là nhập đơn đầu ca, lập tuyến theo kho và báo ngoại lệ điều phối.

**Điều kiện nghiệm thu** — Thử lại sự kiện không tạo cảnh báo trùng. Điều phối viên thấy sự kiện nào kích hoạt luồng và việc gì đã được tạo. Các chính sách chuẩn bị, bằng chứng và hàng nhạy cảm chỉ xuất hiện trong bảng chọn khi API và ứng dụng tài xế đã hỗ trợ hành vi tương ứng.
