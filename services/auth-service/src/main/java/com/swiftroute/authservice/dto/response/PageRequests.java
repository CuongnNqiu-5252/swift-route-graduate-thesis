package com.swiftroute.authservice.dto.response;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;

public final class PageRequests {
    private PageRequests() {}

    public static PageRequest newestFirst(int page, int size, String timestamp) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, timestamp, "id"));
    }
}
