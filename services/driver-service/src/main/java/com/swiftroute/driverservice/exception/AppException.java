package com.swiftroute.driverservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Base exception for all driver-service business errors.
 * All business exceptions extend this class;
 * GlobalExceptionHandler only needs to catch one AppException type.
 */
@Getter
@RequiredArgsConstructor
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
}
