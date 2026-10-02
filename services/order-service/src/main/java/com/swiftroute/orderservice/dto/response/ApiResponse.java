package com.swiftroute.orderservice.dto.response;

import java.time.Instant;

import com.swiftroute.orderservice.exception.ErrorCode;

/**
 * Format API response thống nhất — đồng bộ với auth-service.
 *
 * Cấu trúc JSON trả về:
 *  {
 *    "data": {...},       // null khi lỗi
 *    "error": {...},      // null khi thành công
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
