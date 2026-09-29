package com.swiftroute.authservice.exception;

public class InvalidCredentialsException extends AppException {

    public InvalidCredentialsException(ErrorCode errorCode) {
        super(errorCode);
    }
}
