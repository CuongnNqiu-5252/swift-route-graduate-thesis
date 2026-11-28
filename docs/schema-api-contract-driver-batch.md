# API thao tác hàng loạt của tài xế

Hai endpoint thuộc order-service, sử dụng cùng cơ chế xác thực và phân quyền tài xế như DeliveryTaskController.

## Nhận chuyến

`POST /api/delivery-tasks/batch-accept`

## Xác nhận lấy hàng tại kho

`POST /api/delivery-tasks/batch-pickup`

Body dùng snake_case:

```json
{
  "task_ids": ["UUID"],
  "order_ids": ["UUID"],
  "notes": "Ghi chú tùy chọn"
}
```

Client cung cấp danh sách task hoặc order cần xử lý. Các ID phải thuộc phạm vi tài xế và doanh nghiệp được backend cho phép. Không gửi yêu cầu lấy hàng cho đơn hoàn, đơn đã hoàn tất hoặc đã hủy.

Response thành công sử dụng ApiResponse; `data` là danh sách DriverDeliveryTaskResponse đã cập nhật:

```json
{
  "data": [],
  "error": null,
  "timestamp": "2026-11-05T00:00:00Z"
}
```

Nhận chuyến chuyển nhiệm vụ phù hợp từ ASSIGNED sang ACCEPTED. Lấy hàng hỗ trợ ASSIGNED/ACCEPTED/ARRIVED chuyển đến OUT_FOR_DELIVERY, đồng thời ghi nhận custody tại điểm lấy và audit của các chuyển trạng thái trung gian trong giao dịch backend. Mobile chỉ hiển thị kết quả thành công sau phản hồi server; optimistic update phải được rollback khi yêu cầu thất bại.

Đường dẫn trên là mapping trực tiếp trong service. Gateway/client phải sử dụng base URL hiện có, không tự đổi sang `/api/v1/orders/driver-tasks`.
