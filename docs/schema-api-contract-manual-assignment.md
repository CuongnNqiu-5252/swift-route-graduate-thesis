# Gán đơn thủ công theo kế hoạch điều phối

`POST /api/orders/assign-batch`

Yêu cầu quyền vận hành và tenant hiện hành. Body:

```json
{"assignments":[{"order_id":"UUID","driver_id":"UUID","plan_id":null}]}
```

`driver_id` chấp nhận ID hồ sơ hoặc ID tài khoản; backend chuẩn hóa thành ID tài khoản trước khi tạo task và tuyến. Tài xế phải sẵn sàng trong doanh nghiệp. Các đơn phải cùng tenant, chưa có kế hoạch và ở trạng thái PAID (web hiển thị READY_FOR_DISPATCH). ID đơn lặp hoặc plan_id có giá trị bị từ chối.

Backend nhóm đơn theo tài xế, tạo DispatchPlan thủ công, liên kết thứ tự điểm theo request, duyệt và dispatch bằng service hiện có trong một giao dịch. Không ghi nhận custody khi gán đơn. Response: ApiResponse với data null khi thành công.

Đây là gán đơn mới; không dùng endpoint này để đổi tài xế của tuyến đang thực thi hoặc chuyển quyền giữ kiện hàng.
