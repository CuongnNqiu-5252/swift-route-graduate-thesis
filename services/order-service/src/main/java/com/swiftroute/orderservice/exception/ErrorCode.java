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

    // ===== Lỗi nghiệp vụ Tenant =====
    TENANT_NOT_FOUND(404, "Doanh nghiệp không tồn tại", HttpStatus.NOT_FOUND),
    TENANT_ALREADY_EXISTS(409, "Tên doanh nghiệp đã tồn tại", HttpStatus.CONFLICT),
    TENANT_SUSPENDED(403, "Tài khoản doanh nghiệp đang bị tạm khóa", HttpStatus.FORBIDDEN),
    TENANT_PENDING(403, "Tài khoản doanh nghiệp chưa được phê duyệt", HttpStatus.FORBIDDEN),
    TENANT_REJECTED(403, "Yêu cầu tạo doanh nghiệp đã bị từ chối", HttpStatus.FORBIDDEN),
    INVALID_TENANT_STATUS_TRANSITION(409, "Không thể chuyển trạng thái doanh nghiệp theo yêu cầu", HttpStatus.CONFLICT),
    OWNER_NOT_ACTIVE(400, "Chủ doanh nghiệp không tồn tại hoặc không hoạt động", HttpStatus.BAD_REQUEST),
    AUTH_SERVICE_UNAVAILABLE(503, "Không thể xác minh chủ doanh nghiệp vào lúc này", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;
}
