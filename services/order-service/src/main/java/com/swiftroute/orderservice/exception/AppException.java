package com.swiftroute.orderservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Exception gốc cho toàn bộ order-service.
 * Đồng bộ với AppException của auth-service:
 *  - Mọi exception nghiệp vụ đều kế thừa class này.
 *  - GlobalExceptionHandler chỉ cần bắt 1 loại AppException là đủ.
 */
@Getter
@RequiredArgsConstructor
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
}
