package com.swiftroute.authservice.exception;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(500, "Lỗi hệ thống không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(401, "Bạn chưa đăng nhập hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(401, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(404, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    USER_EXISTED(400, "Email này đã được sử dụng", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(401, "Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_VERIFIED(403, "Verify your email before creating an organization", HttpStatus.FORBIDDEN),
    INVALID_VERIFICATION_TOKEN(400, "Verification link is invalid, expired or already used", HttpStatus.BAD_REQUEST),
    VERIFICATION_RATE_LIMITED(429, "Wait before requesting another verification email", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_PASSWORD_RESET_TOKEN(400, "Liên kết đặt lại mật khẩu không hợp lệ, đã hết hạn hoặc đã được sử dụng", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(400, "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED(403, "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),

    // ===== Tenant Membership & Invitation Errors =====
    TENANT_MEMBERSHIP_NOT_FOUND(404, "Không tìm thấy thành viên trong doanh nghiệp", HttpStatus.NOT_FOUND),
    TENANT_MEMBERSHIP_ALREADY_EXISTS(409, "Người dùng này đã là thành viên của doanh nghiệp", HttpStatus.CONFLICT),
    TENANT_MEMBERSHIP_SUSPENDED(403, "Tài khoản của bạn tại doanh nghiệp này đã bị tạm khóa", HttpStatus.FORBIDDEN),
    CANNOT_SUSPEND_LAST_OWNER(400, "Không thể khóa chủ sở hữu (Owner) duy nhất của doanh nghiệp", HttpStatus.BAD_REQUEST),
    INVITATION_NOT_FOUND(404, "Lời mời không tồn tại hoặc token không hợp lệ", HttpStatus.NOT_FOUND),
    INVITATION_EXPIRED(400, "Lời mời đã hết hạn", HttpStatus.BAD_REQUEST),
    INVITATION_ALREADY_PROCESSED(400, "Lời mời này đã được chấp nhận hoặc đã bị thu hồi", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;
}
