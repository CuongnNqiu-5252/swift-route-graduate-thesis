package com.swiftroute.authservice.exception;

public class TokenReuseDetectedException extends AppException {
    public TokenReuseDetectedException(ErrorCode errorCode) {
        super(errorCode);
    }
}
