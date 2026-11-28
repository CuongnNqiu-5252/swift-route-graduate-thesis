package com.swiftroute.orderservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import com.swiftroute.orderservice.dto.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Xử lý lỗi tập trung — đồng bộ mô hình với GlobalExceptionHandler của auth-service.
 *
 * Thứ tự bắt lỗi:
 *  1. AppException  → lỗi nghiệp vụ có kiểm soát (Order/Tenant không tìm thấy, v.v.)
 *  2. MethodArgumentNotValidException → lỗi @Valid trên DTO
 *  3. MissingRequestHeaderException   → thiếu header bắt buộc (vd: Idempotency-Key, X-Service-Key)
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

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("Resource not found: {}", ex.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * Trả về 400 nếu thiếu Idempotency-Key, và 401 nếu thiếu header xác thực nội bộ (vd: X-Service-Key).
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        if ("Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ErrorCode.VALIDATION_ERROR));
        }
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.UNAUTHENTICATED));
    }

    /**
     * Bỏ qua lỗi ngắt kết nối tự nhiên khi client đóng tab/reload trang trong luồng SSE streaming.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncDisconnect(AsyncRequestNotUsableException ex) {
        log.info("Client disconnected from SSE tracking stream: {}", ex.getMessage());
    }

    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> conflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ErrorCode.CONCURRENT_UPDATE));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> invalidArgument(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected system error: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.UNCATEGORIZED_EXCEPTION));
    }
}
