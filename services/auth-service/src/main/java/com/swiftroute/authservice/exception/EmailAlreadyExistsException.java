package com.swiftroute.authservice.exception;

public class EmailAlreadyExistsException extends AppException {
    public EmailAlreadyExistsException(ErrorCode errorCode) {
        super(errorCode);
    }
}
