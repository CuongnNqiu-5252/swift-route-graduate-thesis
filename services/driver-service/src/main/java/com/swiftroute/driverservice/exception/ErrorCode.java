package com.swiftroute.driverservice.exception;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Error codes for driver-service.
 * Each code contains: code (HTTP numeric), message, statusCode (HTTP Status).
 */
@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ErrorCode {

    // ===== General errors =====
    UNCATEGORIZED_EXCEPTION(500, "Uncategorized system error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(401, "Unauthenticated or invalid token", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(403, "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR(400, "Invalid input data", HttpStatus.BAD_REQUEST),

    // ===== Driver business errors =====
    DRIVER_NOT_FOUND(404, "Driver not found", HttpStatus.NOT_FOUND),
    DRIVER_ALREADY_EXISTS(409, "Driver already exists", HttpStatus.CONFLICT),
    DRIVER_SUSPENDED(403, "Driver account is suspended", HttpStatus.FORBIDDEN),
    EARNING_NOT_FOUND(404, "Driver earning not found", HttpStatus.NOT_FOUND),
    SETTLEMENT_NOT_FOUND(404, "Driver settlement not found", HttpStatus.NOT_FOUND),
    SETTLEMENT_EMPTY(409, "No pending earnings are available for this settlement", HttpStatus.CONFLICT),
    INVALID_SETTLEMENT_STATUS(409, "Settlement status does not allow this action", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;
}
