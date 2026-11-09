package com.swiftroute.orderservice.exception;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Đồng bộ cấu trúc với ErrorCode của auth-service.
 * Mỗi mã lỗi gồm: code (số HTTP), message (tiếng Việt), statusCode (HTTP
 * Status).
 */
@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ErrorCode {
    // ===== Lỗi chung =====
    UNCATEGORIZED_EXCEPTION(500, "Lỗi hệ thống không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(401, "Bạn chưa đăng nhập hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(403, "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR(400, "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),

    // ===== Lỗi nghiệp vụ Order =====
    ORDER_NOT_FOUND(404, "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATUS(400, "Trạng thái đơn hàng không hợp lệ hoặc không thể chuyển đổi", HttpStatus.BAD_REQUEST),
    ORDER_ALREADY_CANCELLED(400, "Đơn hàng đã bị huỷ, không thể cập nhật", HttpStatus.BAD_REQUEST),
    ORDER_ACCESS_DENIED(403, "Bạn không có quyền truy cập đơn hàng này", HttpStatus.FORBIDDEN),

    // ===== Payment / COD Ledger =====
    PAYMENT_NOT_FOUND(404, "Không tìm thấy nghĩa vụ thanh toán COD", HttpStatus.NOT_FOUND),
    COD_COLLECTION_NOT_FOUND(404, "Không tìm thấy khoản thu COD", HttpStatus.NOT_FOUND),
    COD_HANDOVER_NOT_FOUND(404, "Không tìm thấy phiếu bàn giao COD", HttpStatus.NOT_FOUND),
    COD_DISPUTE_NOT_FOUND(404, "Không tìm thấy chênh lệch COD", HttpStatus.NOT_FOUND),
    INVALID_COD_HANDOVER(409, "Phiếu bàn giao COD không hợp lệ hoặc đã được xử lý", HttpStatus.CONFLICT),
    INVALID_COD_DISPUTE(409, "Chênh lệch COD không còn ở trạng thái có thể xử lý", HttpStatus.CONFLICT),
    COD_REASON_REQUIRED(400, "Cần nhập lý do khi số tiền COD không khớp", HttpStatus.BAD_REQUEST),

    // ===== Lỗi nghiệp vụ Tenant =====
    TENANT_NOT_FOUND(404, "Doanh nghiệp không tồn tại", HttpStatus.NOT_FOUND),
    TENANT_ALREADY_EXISTS(409, "Tên doanh nghiệp đã tồn tại", HttpStatus.CONFLICT),
    TENANT_SUSPENDED(403, "Tài khoản doanh nghiệp đang bị tạm khóa", HttpStatus.FORBIDDEN),
    TENANT_PENDING(403, "Tài khoản doanh nghiệp chưa được phê duyệt", HttpStatus.FORBIDDEN),
    TENANT_REJECTED(403, "Yêu cầu tạo doanh nghiệp đã bị từ chối", HttpStatus.FORBIDDEN),
    INVALID_TENANT_STATUS_TRANSITION(409, "Không thể chuyển trạng thái doanh nghiệp theo yêu cầu", HttpStatus.CONFLICT),
    EMAIL_NOT_VERIFIED(403, "Verify email before creating an organization", HttpStatus.FORBIDDEN),
    CONCURRENT_UPDATE(409, "Data changed concurrently; refresh and retry", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(409, "Idempotency key was already used for a different request", HttpStatus.CONFLICT),
    VERIFICATION_INCOMPLETE(422, "Complete business profile, pickup point and delivery settings before submission", HttpStatus.UNPROCESSABLE_ENTITY),
    VERIFICATION_NOT_FOUND(404, "Verification request not found", HttpStatus.NOT_FOUND),
    INVALID_VERIFICATION_TRANSITION(409, "Invalid or concurrent verification decision", HttpStatus.CONFLICT),
    OPERATIONS_NOT_READY(409, "Tenant is not ready for this operation; check operational-readiness", HttpStatus.CONFLICT),
    OWNER_NOT_ACTIVE(400, "Chủ doanh nghiệp không tồn tại hoặc không hoạt động", HttpStatus.BAD_REQUEST),
    AUTH_SERVICE_UNAVAILABLE(503, "Không thể xác minh chủ doanh nghiệp vào lúc này", HttpStatus.SERVICE_UNAVAILABLE),

    // ===== Lỗi nghiệp vụ Dispatch Plan =====
    PLAN_NOT_FOUND(404, "Không tìm thấy kế hoạch điều phối", HttpStatus.NOT_FOUND),
    INVALID_PLAN_STATUS(400, "Trạng thái kế hoạch không hợp lệ hoặc không thể chuyển đổi", HttpStatus.BAD_REQUEST),
    PLAN_ALREADY_CANCELLED(400, "Kế hoạch điều phối đã bị huỷ, không thể cập nhật", HttpStatus.BAD_REQUEST),

    // ===== Lỗi nghiệp vụ Pickup Locations =====
    PICKUP_LOCATION_NOT_FOUND(404, "Điểm lấy hàng không tồn tại hoặc không thuộc doanh nghiệp của bạn", HttpStatus.NOT_FOUND),
    PICKUP_LOCATION_INACTIVE(400, "Điểm lấy hàng đang ngừng hoạt động", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_DEFAULT_PICKUP_LOCATION(400, "Không thể xoá điểm lấy hàng mặc định duy nhất đang hoạt động", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;
}
