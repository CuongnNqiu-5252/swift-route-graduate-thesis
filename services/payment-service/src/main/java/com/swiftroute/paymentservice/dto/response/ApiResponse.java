package com.swiftroute.paymentservice.dto.response;

import java.time.Instant;
public record ApiResponse<T>(T data, String error, Instant timestamp) {
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(data, null, Instant.now()); }
}
