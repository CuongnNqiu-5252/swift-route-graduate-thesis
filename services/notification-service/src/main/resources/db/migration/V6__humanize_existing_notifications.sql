-- V6__humanize_existing_notifications.sql
-- Replace raw system codes and technical event payloads with natural, human-readable Vietnamese business descriptions.

UPDATE notification_deliveries
SET title = 'Hồ sơ xác minh doanh nghiệp đã được gửi',
    body = 'Hồ sơ xác minh doanh nghiệp đã được tiếp nhận và đang chờ ban quản trị phê duyệt. Hệ thống sẽ thông báo ngay khi có kết quả.'
WHERE template_code = 'tenant.verification_submitted.v1'
   OR body LIKE 'Trạng thái: tenant.verification_submitted.v1%';

UPDATE notification_deliveries
SET title = 'Khởi tạo doanh nghiệp thành công',
    body = 'Không gian làm việc cho doanh nghiệp đã sẵn sàng. Hãy hoàn thiện hồ sơ xác minh để bắt đầu vận hành.'
WHERE template_code = 'tenant.created.v1'
  AND (title = 'Cập nhật workspace SwiftRoute' OR body LIKE 'Trạng thái: tenant.created.v1%');

UPDATE notification_deliveries
SET title = 'Lộ trình giao hàng đã hoàn tất',
    body = 'Tài xế đã hoàn thành tất cả các điểm dừng trong lộ trình giao hàng.'
WHERE template_code = 'plan.completed'
  AND (title LIKE 'Thông báo vận hành: %' OR body LIKE '%plan.completed%');

UPDATE notification_deliveries
SET title = 'Hồ sơ doanh nghiệp đã được phê duyệt',
    body = 'Hồ sơ doanh nghiệp đã được phê duyệt thành công. Bạn hiện có thể tạo đơn hàng và bắt đầu điều phối giao vận.'
WHERE body LIKE 'Trạng thái: tenant.approved.v1%'
   OR (template_code = 'tenant.approved.v1' AND title = 'Cập nhật workspace SwiftRoute');

UPDATE notification_deliveries
SET title = 'Yêu cầu bổ sung hồ sơ xác minh doanh nghiệp',
    body = regexp_replace(body, '^Trạng thái: tenant\.verification_changes_requested\.v1( - )?', 'Yêu cầu bổ sung thông tin hồ sơ: ')
WHERE body LIKE 'Trạng thái: tenant.verification_changes_requested.v1%';

UPDATE notification_deliveries
SET title = 'Hồ sơ doanh nghiệp không được phê duyệt',
    body = regexp_replace(body, '^Trạng thái: tenant\.rejected\.v1( - )?', 'Hồ sơ doanh nghiệp không được phê duyệt: ')
WHERE body LIKE 'Trạng thái: tenant.rejected.v1%';

UPDATE notification_deliveries
SET title = 'Doanh nghiệp đã bị tạm ngưng hoạt động',
    body = regexp_replace(body, '^Trạng thái: tenant\.suspended\.v1( - )?', 'Tài khoản doanh nghiệp tạm ngưng hoạt động: ')
WHERE body LIKE 'Trạng thái: tenant.suspended.v1%';

UPDATE notification_deliveries
SET body = replace(replace(body, '[TRAFFIC_CONGESTION]', 'Tắc đường / kẹt xe nghiêm trọng'), 'MANUAL_REVIEW', 'Chờ điều phối viên xử lý')
WHERE body LIKE '%[TRAFFIC_CONGESTION]%';

UPDATE notification_deliveries
SET body = regexp_replace(body, '^Trạng thái: tenant\.[a-z0-9_.]+(\.v\d+)?( - )?', 'Cập nhật trạng thái doanh nghiệp: ')
WHERE body LIKE 'Trạng thái: tenant.%';
