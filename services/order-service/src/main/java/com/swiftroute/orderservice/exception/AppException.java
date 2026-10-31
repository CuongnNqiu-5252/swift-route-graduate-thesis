package com.swiftroute.orderservice.exception;

import lombok.Getter;

/**
 * Exception gốc cho toàn bộ order-service.
 * Đồng bộ với AppException của auth-service:
 * - Mọi exception nghiệp vụ đều kế thừa class này.
 * - GlobalExceptionHandler chỉ cần bắt 1 loại AppException là đủ.
 */
@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode != null ? errorCode.getMessage() : "Application Exception");
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
