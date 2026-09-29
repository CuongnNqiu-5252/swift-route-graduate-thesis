# Kế hoạch triển khai chi tiết

## Hệ thống SaaS tối ưu lịch trình giao hàng — Microservices + DP & Học máy

> Giả định tổng thời gian: **16 tuần** (~4 tháng, phổ biến cho 1 kỳ làm luận văn tốt nghiệp). Nếu trường em cho thời gian khác, cấu trúc giai đoạn giữ nguyên, chỉ co giãn số tuần theo tỷ lệ.

---

## Nguyên tắc phân bổ thời gian

- **40% thời gian** dành cho phần lõi thuật toán (DP + ML) — đây là phần "luận văn" thật sự, quyết định điểm
- **35% thời gian** dành cho kiến trúc hệ thống (microservices, mobile, web)
- **15% thời gian** dành cho AI Agent (phần nâng cao, làm sau khi lõi ổn định)
- **10% thời gian** dự phòng (buffer) cho rủi ro phát sinh, viết báo cáo

---

## Giai đoạn 0 — Chuẩn bị (Tuần 1-2)

| Công việc | Chi tiết | Kết quả cần đạt |
|---|---|---|
| Khảo sát & tổng hợp tài liệu | Đọc paper liên quan (DPDP, VRP, ML pruning, multi-agent negotiation) | Danh mục tài liệu tham khảo (10-15 nguồn) |
| Xác định phạm vi cuối cùng | Chốt: mô hình hybrid tài xế, danh sách use case, danh sách service | Bản đặc tả yêu cầu (SRS) sơ bộ |
| Thiết kế kiến trúc tổng thể | Vẽ sơ đồ microservices, ERD từng service, API contract nháp | Sơ đồ kiến trúc (đã có ở file trước) |
| Setup môi trường | Docker, repo Git, CI cơ bản (lint/test tự động) | Repo khởi tạo, chạy được `docker compose up` với hạ tầng rỗng |
| Chuẩn bị dữ liệu mẫu | Viết script sinh dữ liệu giả lập (đơn hàng, tài xế, toạ độ) bằng Faker + phân phối Poisson | Bộ dữ liệu mẫu 500-1000 đơn để test thuật toán |

**Cột mốc (Milestone 0)**: Bảo vệ đề cương (nếu trường yêu cầu) — trình bày use case, kiến trúc, kế hoạch.

---

## Giai đoạn 1 — Xây dựng lõi thuật toán (Tuần 3-7, ~5 tuần)

Đây là phần quan trọng nhất, làm trước tiên để có thời gian tinh chỉnh nếu gặp khó khăn.

### Tuần 3: DP thuần
- Cài đặt Held-Karp (bitmask DP) cho bài toán TSP đơn tài xế
- Test với n nhỏ (5-15 điểm), đo thời gian chạy thực tế
- **Deliverable**: module DP chạy đúng, có unit test so sánh với brute-force ở n rất nhỏ

### Tuần 4: Baseline so sánh
- Cài đặt Nearest Neighbor heuristic
- Cài đặt Genetic Algorithm (hoặc dùng thư viện có sẵn) làm baseline thứ 2
- **Deliverable**: 3 phương pháp baseline chạy được trên cùng bộ dữ liệu, có bảng so sánh sơ bộ

### Tuần 5-6: ML Pruning Layer
- Sinh dữ liệu huấn luyện: chạy DP đầy đủ trên bộ dữ liệu nhỏ (n ≤ 15) để lấy nhãn "ground truth" cho từng bước transition
- Trích đặc trưng (feature engineering): khoảng cách, độ gấp deadline, mật độ lân cận...
- Huấn luyện model (thử cả Gradient Boosting và MLP nhỏ, chọn cái tốt hơn)
- Tích hợp model vào DP engine (top-k pruning)
- **Deliverable**: DP+ML chạy được trên n lớn (50-100 điểm), có log thời gian + chất lượng lời giải

### Tuần 7: Tầng gán cụm (Clustering) + Đánh giá thuật toán
- Cài đặt thuật toán gán đơn cho tài xế (K-means theo vị trí + ràng buộc capacity, hoặc greedy assignment)
- Nối 2 tầng: Clustering → DP+ML
- Chạy thực nghiệm đầy đủ: so sánh DP thuần / DP+ML / Nearest Neighbor / Genetic Algorithm trên nhiều quy mô n (10, 30, 50, 100 điểm)
- **Deliverable**: Bảng số liệu + biểu đồ so sánh (thời gian chạy, % lệch tối ưu) — đây là **kết quả nghiên cứu chính của luận văn**

**Cột mốc (Milestone 1)**: Thuật toán lõi hoạt động, có số liệu thực nghiệm rõ ràng để đưa vào chương 5.

---

## Giai đoạn 2 — Xây dựng Microservices (Tuần 8-11, ~4 tuần)

### Tuần 8: Service nền tảng
- Routing Engine Service: đóng gói thuật toán từ Giai đoạn 1 thành API FastAPI (`/optimize/batch`, `/optimize/insert`)
- Order Service, Driver Service (NestJS): CRUD cơ bản, kết nối DB riêng
- **Deliverable**: 3 service chạy độc lập qua Docker, có Postman collection test API

### Tuần 9: Kết nối liên service
- Auth Service: đăng ký/đăng nhập, phân quyền theo vai (JWT)
- Kết nối Order + Driver → Routing Engine (luồng UC3 — Lập kế hoạch ca)
- Thiết lập RabbitMQ cho sự kiện bất đồng bộ (`order.created`, `order.urgent`)
- **Deliverable**: Luồng "tạo đơn → lập kế hoạch → nhận lộ trình" chạy end-to-end qua Postman/script test

### Tuần 10: Notification + API Gateway
- Notification Service (push notification giả lập hoặc tích hợp Firebase)
- API Gateway (Spring Cloud Gateway) — gộp route tất cả service
- **Deliverable**: Toàn bộ backend chạy qua 1 cổng vào duy nhất

### Tuần 11: Web Admin Dashboard
- React + TypeScript: trang đăng nhập, xem đơn hàng, kích hoạt lập kế hoạch, xem bản đồ lộ trình (Leaflet/Mapbox)
- Dashboard báo cáo cơ bản (chi phí, số đơn đúng hạn)
- **Deliverable**: Web admin xem/thao tác được với dữ liệu thật từ backend

**Cột mốc (Milestone 2)**: Demo được luồng "Nhân viên vận hành lập kế hoạch → xem lộ trình trên web" hoàn chỉnh.

---

## Giai đoạn 3 — Mobile App (Tuần 12-13, ~2 tuần)

### Tuần 12: App Tài xế
- Flutter: đăng nhập, xem lộ trình được giao, cập nhật vị trí GPS, đánh dấu đã giao/báo sự cố
- Kết nối WebSocket để cập nhật vị trí real-time
- **Deliverable**: App tài xế chạy trên emulator, đồng bộ dữ liệu với backend

### Tuần 13: App Khách hàng
- Flutter: đặt đơn, chọn khung giờ, theo dõi vị trí tài xế real-time, xem lý do sắp xếp
- **Deliverable**: App khách hàng chạy trên emulator, luồng đặt đơn → theo dõi hoàn chỉnh

**Cột mốc (Milestone 3)**: Demo full luồng 3 phía (Web admin - Mobile tài xế - Mobile khách hàng) đồng bộ real-time.

---

## Giai đoạn 4 — AI Agent & Tính năng nâng cao (Tuần 14-15, ~2 tuần)

> Nếu Giai đoạn 1-3 bị trễ, đây là phần có thể cắt giảm/đơn giản hoá trước tiên (ưu tiên giữ Exception Agent, có thể lược Watchdog/Advisor nếu thiếu thời gian).

### Tuần 14: Exception Handling Agent
- Cài đặt dynamic insertion (chèn đơn khẩn vào lộ trình có sẵn)
- Luồng chấp nhận/từ chối cho tài xế pool, cơ chế tìm tài xế thay thế
- **Deliverable**: UC7 chạy được, demo tình huống đơn khẩn + tài xế từ chối

### Tuần 15: Ops Watchdog Agent + Business Advisor Agent (tuỳ thời gian còn lại)
- Watchdog: rule/threshold đơn giản trên log quyết định (không cần ML phức tạp, có thể bắt đầu bằng rule-based)
- Advisor: tích hợp LLM API (Claude/GPT) + query dữ liệu cơ bản (RAG đơn giản hoặc chỉ truy vấn SQL có cấu trúc theo intent)
- **Deliverable**: Demo 1 kịch bản watchdog cảnh báo + 1 đoạn hội thoại advisor trả lời đúng

**Cột mốc (Milestone 4)**: Toàn bộ tính năng đã lên kế hoạch hoạt động, hệ thống hoàn chỉnh.

---

## Giai đoạn 5 — Kiểm thử, tối ưu & viết báo cáo (Tuần 16, + buffer)

| Công việc | Chi tiết |
|---|---|
| Load testing | Dùng k6/JMeter test từng service, đo khả năng scale độc lập (`docker compose up --scale routing-engine-service=3`) |
| Sửa lỗi, dọn code | Code review lại, viết docstring/comment, đảm bảo demo mượt |
| Viết báo cáo luận văn | Hoàn thiện 6 chương theo cấu trúc đã thống nhất, chèn số liệu thực nghiệm từ Giai đoạn 1 |
| Chuẩn bị slide + kịch bản demo | Theo kịch bản đã thiết kế (bắn đơn khẩn giữa giờ cao điểm, scale service...) |
| Chạy thử buổi bảo vệ (mock defense) | Nhờ bạn bè/người quen đóng vai hội đồng, tập trả lời câu hỏi |

**Cột mốc (Milestone 5 — Final)**: Sẵn sàng bảo vệ.

---

## Bảng tổng hợp theo tuần (Gantt rút gọn)

| Tuần | Nội dung chính |
|---|---|
| 1-2 | Chuẩn bị, kiến trúc, dữ liệu mẫu |
| 3 | DP thuần |
| 4 | Baseline (Nearest Neighbor, Genetic Algorithm) |
| 5-6 | ML Pruning Layer |
| 7 | Clustering + đánh giá thuật toán (số liệu chính) |
| 8 | Routing Engine API + Order/Driver Service |
| 9 | Auth Service + kết nối liên service + RabbitMQ |
| 10 | Notification Service + API Gateway |
| 11 | Web Admin Dashboard |
| 12 | Mobile App - Tài xế |
| 13 | Mobile App - Khách hàng |
| 14 | Exception Handling Agent |
| 15 | Ops Watchdog + Business Advisor Agent |
| 16 | Kiểm thử, viết báo cáo, chuẩn bị bảo vệ |

---

## Rủi ro & phương án dự phòng

| Rủi ro | Ảnh hưởng | Phương án |
|---|---|---|
| ML Pruning không cải thiện rõ so với DP thuần | Mất điểm phần "đóng góp khoa học" | Chuẩn bị sẵn baseline Genetic Algorithm để vẫn có so sánh ý nghĩa dù ML chưa tối ưu tuyệt đối |
| Microservices tốn quá nhiều thời gian setup | Trễ tiến độ tổng thể | Có thể gộp tạm 2-3 service nhỏ (VD Notification + Auth) thành 1 service ở bản demo đầu, tách ra sau nếu còn thời gian |
| Mobile app 2 vai tốn thời gian hơn dự kiến | Trễ Giai đoạn 3 | Ưu tiên làm App Tài xế trước (gắn với luồng thuật toán chính), App Khách hàng có thể làm ở mức tối giản (UI đơn giản hơn) |
| AI Agent phức tạp hơn dự kiến | Trễ Giai đoạn 4 | Exception Agent là bắt buộc; Watchdog/Advisor có thể lược bớt, đưa vào "hướng phát triển" trong kết luận |
| Dữ liệu giả lập không đủ thực tế | Kết quả thực nghiệm thiếu thuyết phục | Tham khảo phân phối đơn hàng thực tế từ dataset công khai (VD dataset giao hàng công khai trên Kaggle) thay vì sinh ngẫu nhiên hoàn toàn |

---

## Checklist trước khi bảo vệ

- [ ] Số liệu so sánh DP thuần vs DP+ML vs heuristic khác — có bảng + biểu đồ rõ ràng
- [ ] Demo chạy được trên máy thật (không phụ thuộc mạng ngoài lúc bảo vệ)
- [ ] Kịch bản demo đã tập dượt ít nhất 2-3 lần, có phương án B nếu demo lỗi (video quay sẵn dự phòng)
- [ ] Trả lời được câu hỏi: "Vì sao dùng DP+ML mà không chỉ dùng heuristic thuần?"
- [ ] Trả lời được câu hỏi: "Điểm khác biệt với Grab/Onfleet là gì?"
- [ ] Trả lời được câu hỏi: "Vì sao tách thành microservices, không làm monolith cho đơn giản?"
- [ ] Slide có sơ đồ kiến trúc, use case, sequence diagram rõ ràng (không chỉ toàn chữ)

---

## Phụ lục 1: Kế hoạch triển khai chi tiết API Gateway

**1. Tầng Security & Xác thực (Đang làm)**
- [ ] Cài đặt dependencies: `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security`.
- [ ] Cấu hình `SecurityWebFilterChain` (Bỏ qua CSRF, cấu hình Public/Private endpoints).
- [ ] Khai báo `ReactiveJwtDecoder` (Dùng Secret Key hoặc Public Key để tự động giải mã JWT).
- [ ] Xử lý lỗi 401/403: Chặn lỗi mặc định của Spring, custom lại để trả về JSON (VD: `{"status": 401, "message": "Unauthorized"}`).

**2. Tầng CORS & Headers**
- [ ] Cấu hình CORS (`CorsWebFilter`) để cho phép React/Flutter gọi API (Allowed origins, methods, headers).
- [ ] (Tuỳ chọn) Trích xuất thông tin từ JWT (VD: `user_id`, `role`) đẩy vào Header (`X-User-Id`) để các microservice con không cần parse lại JWT.

**3. Tầng Routing (Cấu hình YAML)**
- [ ] Định nghĩa Route cho `auth-service` (Đã xong).
- [ ] Định nghĩa Route cho `order-service` (Tương lai).
- [ ] Định nghĩa Route cho `routing-engine` (Tương lai).

**4. Tầng Quan sát & Ổn định (Observability & Resilience)**
- [ ] Hoàn thiện `CustomGlobalFilter` để ghi log (Thời gian bắt đầu, kết thúc, URI, IP).
- [ ] Cấu hình Fallback/Circuit Breaker (Nếu service con sập, Gateway trả JSON 503 thân thiện).
- [ ] (Tuỳ chọn) Tích hợp Redis Rate Limiter để chống Spam.
