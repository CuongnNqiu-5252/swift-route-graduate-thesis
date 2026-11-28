package com.swiftroute.driverservice.exception;

import lombok.Getter;

/**
 * Base exception for all driver-service business errors.
 */
@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode != null ? errorCode.getMessage() : null);
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null && !customMessage.isBlank()
                ? customMessage
                : (errorCode != null ? errorCode.getMessage() : null));
        this.errorCode = errorCode;
    }
}
