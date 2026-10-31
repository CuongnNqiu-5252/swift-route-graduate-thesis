package com.swiftroute.driverservice.dto.response;

import java.time.Instant;

import com.swiftroute.driverservice.exception.ErrorCode;

/**
 * Unified API response format — mirrors ApiResponse of order-service.
 *
 * JSON structure:
 *  {
 *    "data": {...},       // null on error
 *    "error": {...},      // null on success
 *    "timestamp": "..."
 *  }
 */
public record ApiResponse<T>(
        T data,
        ErrorCode error,
        Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(ErrorCode error) {
        return new ApiResponse<>(null, error, Instant.now());
    }
}
