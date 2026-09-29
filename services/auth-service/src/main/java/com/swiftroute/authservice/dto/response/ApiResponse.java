package com.swiftroute.authservice.dto.response;

import java.time.Instant;

import com.swiftroute.authservice.exception.ErrorCode;

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
