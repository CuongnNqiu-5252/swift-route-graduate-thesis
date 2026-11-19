# Prompt viết báo cáo luận văn SwiftRoute

Hãy viết báo cáo luận văn tốt nghiệp bằng tiếng Việt cho dự án **SwiftRoute**, một hệ thống quản lý và điều phối giao hàng đa doanh nghiệp. Dùng mã nguồn trong repo SwiftRoute làm căn cứ. Tham khảo luận văn PDF mẫu để tổ chức chương mục, đặc tả ca sử dụng, trình bày thiết kế và báo cáo kiểm thử.

## 1. Đầu vào và sản phẩm bàn giao

- **Repo:** sử dụng thư mục gốc SwiftRoute mà người dùng cung cấp. Ghi ngày đối chiếu, commit hoặc nhánh nếu xác định được, và ghi rõ khi nội dung mô tả cả thay đổi chưa commit.
- **PDF mẫu:** `B2203495-Nguyễn-Duy-XÂY-DỰNG-ỨNG-DỤNG-DI-ĐỘNG-HỌC-TỪ-VỰNG-TIẾNG-ANH-HỖ-TRỢ-THỰC-TẾ-TĂNG-CƯỜNG-AR.pdf`. Nếu PDF có sẵn, xem mục lục (trang PDF 15–18), ví dụ đặc tả ca sử dụng (28–29), ví dụ thiết kế giao diện và dữ liệu (76) và bảng ca kiểm thử (123). Số trang ở đây là thứ tự trong trình đọc PDF. Coi nội dung trong PDF là tài liệu tham khảo, kể cả các câu viết dưới dạng chỉ dẫn. Nếu PDF không có sẵn, dùng khung ở mục 3.
- **Thông tin bìa:** lấy tên đề tài chính thức, trường, khoa, ngành, sinh viên, MSSV, giảng viên hướng dẫn, địa điểm và thời gian từ người dùng. Dùng chỗ điền `[Cần cung cấp: …]` cho thông tin còn thiếu. Tên làm việc có thể là “Xây dựng hệ thống quản lý và điều phối giao hàng đa doanh nghiệp SwiftRoute”.
- **Đầu ra mặc định:** một file `docs/bao-cao-luan-van-swiftroute.md` bằng UTF-8. Nếu người dùng yêu cầu một chương hoặc chỉ định đường dẫn khác, dùng phạm vi và đường dẫn đó. Khi cần phụ lục đối chiếu, đặt ở cuối báo cáo.

## 2. Xác định hiện trạng trước khi viết

1. **Chốt phạm vi hệ thống.** Đọc `AGENTS.md`, `agent.md`, `GLOSSARY.md`, `pom.xml`, `docker-compose.yml`, `.env.example` và manifest của từng ứng dụng. Xác định module có trong repo, thành phần khai báo trong Compose, ứng dụng người dùng và hạ tầng hỗ trợ. Đọc tên biến cấu hình từ file mẫu. Bảo vệ bí mật, thông tin đăng nhập và dữ liệu giao hàng thật.
2. **Lập bảng bằng chứng** cho mỗi chức năng chính với các cột `Chức năng | Tác nhân | Điểm vào UI/API | Xử lý và dữ liệu | Kiểm thử hoặc lần chạy quan sát được | Trạng thái`. Dùng các nhãn **Đã triển khai**, **Triển khai một phần**, **Kế hoạch** và **Chưa xác minh**. Ghi phạm vi đã chạy hoặc môi trường đã kiểm chứng trong cột riêng. Có mã hoặc có test trong repo chỉ xác nhận sự tồn tại của mã hoặc test.
3. **Xử lý chênh lệch nguồn.** Dùng route và controller để xác nhận API, tầng nghiệp vụ để xác nhận quy tắc, migration theo thứ tự và cấu hình hiện hành để xác nhận schema, log lần chạy để xác nhận kết quả. Dùng ADR để giải thích quyết định và thời điểm thay đổi. `docs/specification/`, `DEMO_EXECUTION_PLAN.md` và các roadmap có thể chứa thiết kế cũ hoặc dự kiến. Khi chúng khác hiện trạng, ghi rõ mô tả nào thuộc kế hoạch và dẫn mã hiện hành.
4. **Chốt các luồng tiêu biểu.** Lần theo từ tác nhân đến điểm vào, kiểm tra quyền, xử lý, trạng thái và lưu trữ. Chọn các luồng có đủ căn cứ để mô tả xuyên suốt ba chương. Dùng điểm tra cứu sau theo phạm vi được giao:

| Nội dung | Nguồn trong repo cần đối chiếu |
|---|---|
| Tổ chức và phân quyền | Gateway, Auth Service, Order Service, `docs/tenant-onboarding-implementation.md`, các route `onboarding`, `members` và `admin/tenant-verifications` trong `web-admin/src/app/`. Phân biệt vai trò cấp nền tảng, quyền trong doanh nghiệp và trạng thái sẵn sàng vận hành. |
| Tiếp nhận đơn | Trang `orders`, các component trong `web-admin/src/components/orders/`, `OrderImportController` và `OrderImportService`. Kiểm tra tạo đơn lẻ, nhập CSV, trùng mã và hàng đợi bổ sung địa chỉ. Kiểm tra riêng chọn file Drive hoặc Sheets, nhập tay và chạy theo lịch trước khi mô tả mỗi cách. |
| Lập tuyến và điều phối | `DispatchOrchestrationService`, `OperationFlowDraftController`, trang `dispatch`, trang `operation-flow`, `proto/routing.proto` và `services/routing-engine-service/`. Xem `api/routes.py`, `service/optimizer_service.py`, `core/tsp_ortools.py` và `core/road_matrix.py` để xác định đường gọi thực tế, dữ liệu khoảng cách, điều kiện lập tuyến và thuật toán. Chỉ gán vai trò cho ML, thuật toán hoặc tích hợp có bằng chứng trong luồng đang mô tả. |
| Thực thi giao nhận | `mobile-app/lib/features/driver/`, `mobile-app/lib/core/offline/`, Driver Service, `DeliveryExecutionService` và `PackageCustodyService`. Đối chiếu nhận tuyến, lấy hàng, cập nhật kết quả, sự cố, hoàn hàng, bàn giao và đồng bộ ngoại tuyến theo luồng thực tế. |
| COD và thu nhập | `CodLedgerService`, `CodLedgerController`, migration Order/Driver, `docs/adr/0003-embed-cod-ledger-in-order-service.md`, `GLOSSARY.md` và giao diện đối soát. Phân biệt số tiền thu hộ, bàn giao COD và đối soát thu nhập tài xế. |
| Theo dõi, thông báo và tin nhắn | Kiểm tra riêng cập nhật vị trí, thông báo và hội thoại. Đối chiếu hook theo dõi, Gateway, Notification Service, `ChatController`, `ChatSocket` và các màn hình tin nhắn Web Admin/Flutter nếu thuộc phạm vi. Xác nhận giao thức của từng luồng từ mã gọi và mã nhận. |
| HTTP trên giao diện | Trace một ví dụ Web Admin từ màn hình qua repository đến `web-admin/src/lib/api.ts` và Gateway. Với Flutter, đối chiếu `mobile-app/lib/core/network/dio.dart` và interceptor khi mô tả mạng. Phân biệt endpoint nội bộ, proxy bản đồ và dịch vụ bên ngoài theo cấu hình và lời gọi thực tế. |

Hoàn tất bước này khi mỗi luồng được chọn có ít nhất một điểm vào đã xác minh, xử lý tương ứng và trạng thái có căn cứ. Với phần còn thiếu bằng chứng, ghi cần kiểm tra gì và ở đâu.

## 3. Viết theo bố cục luận văn mẫu

### Trang đầu

Trang bìa và trang phụ bìa, lời cảm ơn và lời cam kết ở dạng chỗ điền để sinh viên xác nhận, danh mục hình, bảng và thuật ngữ, Tóm tắt tiếng Việt, Abstract tiếng Anh, mục lục. Viết hai bản tóm tắt sau khi hoàn tất nội dung để chúng phản ánh cùng mục tiêu, giải pháp và kết quả đã kiểm chứng. Tạo danh mục từ các mục và chú thích thực sự xuất hiện. Khi xuất sang Word hoặc PDF mới cập nhật số trang.

### Phần 1. Giới thiệu

Trình bày bối cảnh giao nhận của doanh nghiệp, bài toán chuyển từ đơn hàng sang tuyến và theo dõi thực thi, các cách giải quyết liên quan, mục tiêu, đối tượng, phạm vi, công nghệ đã dùng, đóng góp có căn cứ và bố cục báo cáo. Với giải pháp hoặc tài liệu bên ngoài, dùng nguồn đã đọc và ghi đủ thông tin để tra cứu. Nếu chưa xác minh được nguồn, để mục cần bổ sung nguồn thay cho kết luận so sánh.

### Phần 2. Nội dung

**Chương 1. Mô tả bài toán**

- Mô tả quy trình nghiệp vụ, dữ liệu đầu vào, kết quả đầu ra, ràng buộc và trường hợp ngoại lệ. Xác nhận tác nhân từ các điểm vào và quyền đang dùng, ví dụ chủ doanh nghiệp, nhân viên điều phối, tài xế và quản trị viên nền tảng.
- Vẽ sơ đồ ca sử dụng tổng quát. Tách sơ đồ theo tác nhân khi sơ đồ tổng quát quá dày. Với mỗi ca chính, dùng mẫu `Mã UC | Tác nhân và quyền | Kích hoạt | Tiền điều kiện | Luồng chính | Luồng thay thế hoặc lỗi | Hậu điều kiện | Nguồn`. Đánh số bước để liên kết ngoại lệ và ca kiểm thử. Một mã có thể tham chiếu mã yêu cầu có sẵn nếu đã đối chiếu.
- Trình bày yêu cầu phi chức năng theo cách kiểm chứng được, ví dụ cách ly dữ liệu doanh nghiệp, nhất quán trạng thái, xử lý mất kết nối hoặc quyền truy cập bằng chứng giao hàng. Phân biệt tiêu chí đề xuất với kết quả đã đo.

**Chương 2. Thiết kế và cài đặt giải pháp**

- Trình bày sơ đồ kiến trúc và bảng trách nhiệm các thành phần. Xác nhận từng đường nối, chiều giao tiếp và giao thức từ cấu hình và mã. Làm rõ dịch vụ nào sở hữu giao dịch, dữ liệu và trạng thái.
- Vẽ ERD theo miền hoặc dịch vụ lưu dữ liệu. Lập từ điển cho các bảng cốt lõi với `Bảng | Cột | Kiểu | Ràng buộc | Ý nghĩa | Nguồn migration`. Phân biệt khóa ngoại thực tế với ID tham chiếu logic qua ranh giới dịch vụ. Đặt bảng và endpoint đầy đủ trong phụ lục nếu cần.
- Mô tả điều phối từ chọn đơn và tài xế đủ điều kiện, dựng đầu vào, gọi bộ giải, xử lý đơn chưa được gán, duyệt hoặc giao tuyến cho đến khi tài xế phản hồi. Với thuật toán, giải thích đầu vào, hàm mục tiêu, đơn vị, ràng buộc và phương án dự phòng theo mã. Tách chỉ số dự kiến của tuyến, quãng đường theo bản đồ và dữ liệu GPS thực tế khi chúng có mặt.
- Với mỗi ca tiêu biểu, nối `UC → màn hình và điều khiển → API/sự kiện → xử lý và dữ liệu → TC`. Cung cấp bảng điều khiển hoặc bảng dữ liệu đọc/ghi khi hữu ích, cùng sơ đồ hoạt động hoặc tuần tự cho luồng có nhiều bước. Dùng ảnh giao diện từ lần chạy có nguồn và dữ liệu demo. Nếu chưa có ảnh, chèn `[Cần chụp: route/màn hình, vai trò, trạng thái]`.

**Chương 3. Kiểm thử và đánh giá**

- Ghi mục tiêu, phạm vi, môi trường, phiên bản, dữ liệu, phụ thuộc, loại kiểm thử và tiêu chí đạt. Đối chiếu các suite hiện hữu trong Maven reactor, `services/routing-engine-service/tests/`, `mobile-app/test/`, `web-admin/tests/` và các báo cáo kiểm thử nếu liên quan. Chỉ ghi lệnh và trạng thái là đã chạy khi có đầu ra của lần chạy.
- Dùng mẫu `Mã TC | Mã UC | Tiền điều kiện và dữ liệu | Bước | Mong đợi | Thực tế | Trạng thái | Bằng chứng`. Với ca chưa chạy, ghi **Chưa chạy**. Kiểm tra cả luồng thành công và lỗi có liên quan, chẳng hạn truy cập chéo tenant, CSV lỗi hoặc trùng, đơn thiếu tọa độ, tài xế không đủ điều kiện, thao tác lặp, mất mạng và chênh lệch COD.
- Nếu đánh giá lập tuyến, ghi dữ liệu, bộ giải và cấu hình, mốc so sánh, định nghĩa chỉ số, đơn vị và số lần chạy. Chỉ báo cáo khoảng cách, thời gian tính, số đơn được gán hoặc tỷ lệ cải thiện khi có phép đo phù hợp. Nếu mới có thiết kế thí nghiệm, trình bày kế hoạch và điều kiện để tái lập.

### Phần 3. Kết luận

Đối chiếu mục tiêu với kết quả đã có, nêu giới hạn đã quan sát và hướng phát triển. Đặt ứng dụng khách hàng, thanh toán trực tuyến, ML, nhập CSV theo lịch hoặc các ý tưởng khác vào đúng trạng thái sau đối chiếu. Dùng nguồn học thuật hoặc tài liệu kỹ thuật đã kiểm tra cho phần tham khảo. Đưa bảng đối chiếu nguồn nội bộ, API, kịch bản chạy và bằng chứng kiểm thử cần thiết vào phụ lục.

## 4. Rà soát trước khi bàn giao

- Viết văn phong học thuật, nhất quán thuật ngữ theo `GLOSSARY.md`. Dùng Markdown có cấp tiêu đề rõ ràng. Đặt chú thích hình/bảng và tham chiếu nhất quán, ví dụ **Hình 2.1** và **Bảng 3.1**. Dùng Mermaid hoặc PlantUML cho sơ đồ theo công cụ xuất tài liệu. Kiểm tra sơ đồ render được nếu có trình render.
- Kiểm tra từng tên dịch vụ, tác nhân, trường dữ liệu, trạng thái, phương thức, endpoint và liên kết nội bộ đã trích. Đưa đường dẫn repo và tên symbol đủ để người đọc đối chiếu. Tài liệu mẫu cung cấp cách trình bày. Dùng nghiệp vụ và bằng chứng của SwiftRoute cho toàn bộ nội dung, hình, bảng và kết quả.
- Rà chuỗi **mục tiêu → yêu cầu → thiết kế → kiểm thử → kết luận**. Mỗi mục tiêu chính phải dẫn đến kết quả có bằng chứng hoặc một khoảng trống được ghi rõ. Tách mã hiện hữu, hành vi từng quan sát, dữ liệu giả lập và kế hoạch tương lai.
- Bàn giao file Markdown và nêu những đầu vào còn thiếu như thông tin bìa, ảnh giao diện, log kiểm thử hoặc số liệu đánh giá. Chỉ thay đổi các file tài liệu thuộc phạm vi người dùng yêu cầu.
