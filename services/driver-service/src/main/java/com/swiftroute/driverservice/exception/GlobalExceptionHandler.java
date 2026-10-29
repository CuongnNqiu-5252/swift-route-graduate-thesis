package com.swiftroute.driverservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftroute.driverservice.dto.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Centralized error handling — mirrors GlobalExceptionHandler of order-service.
 *
 * Catch order:
 * 1. AppException → controlled business errors
 * 2. MethodArgumentNotValidException → @Valid errors on DTOs
 * 3. MissingRequestHeaderException → missing X-User-Id / X-Tenant-Id header
 * from Gateway
 * 4. Exception (generic) → final safety net → 500
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
        ex.getBindingResult().getFieldErrors().forEach(
                fe -> log.warn("Validation failed — field: {}, message: {}", fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR));
    }

    /**
     * Returns 401 when Gateway headers are missing (e.g. calling directly without
     * Gateway).
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
