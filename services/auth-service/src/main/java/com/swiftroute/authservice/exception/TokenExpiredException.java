package com.swiftroute.authservice.exception;

public class TokenExpiredException extends AppException {
    public TokenExpiredException(ErrorCode errorCode) {
        super(errorCode);
    }
}
