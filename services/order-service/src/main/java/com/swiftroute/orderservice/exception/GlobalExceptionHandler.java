package com.swiftroute.orderservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftroute.orderservice.dto.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Xử lý lỗi tập trung — đồng bộ mô hình với GlobalExceptionHandler của auth-service.
 *
 * Thứ tự bắt lỗi:
 *  1. AppException  → lỗi nghiệp vụ có kiểm soát (Order/Tenant không tìm thấy, v.v.)
 *  2. MethodArgumentNotValidException → lỗi @Valid trên DTO
 *  3. MissingRequestHeaderException   → thiếu header X-User-Id, X-Tenant-Id từ Gateway
 *  4. Exception (generic)             → lưới vét cuối — 500
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        // Log field lỗi để debug, không lộ chi tiết ra client
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                log.warn("Validation failed — field: {}, message: {}", fe.getField(), fe.getDefaultMessage())
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR));
    }

    /**
     * Khi Gateway không inject header (ví dụ test trực tiếp bỏ qua Gateway).
     * Trả về 401 thay vì 400 vì thiếu header = chưa qua xác thực.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.UNAUTHENTICATED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected system error: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.UNCATEGORIZED_EXCEPTION));
    }
}
