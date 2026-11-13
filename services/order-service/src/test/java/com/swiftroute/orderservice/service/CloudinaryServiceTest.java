package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;

class CloudinaryServiceTest {
    @Test
    void missingCloudinaryConfigurationFailsClosedInsteadOfReturningDemoEvidence() {
        var service = new CloudinaryService(null);
        var file = new MockMultipartFile("file", "proof.jpg", "image/jpeg", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.uploadImage(file, "pod"))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.EVIDENCE_STORAGE_UNAVAILABLE);
    }

    @Test
    void nonImagePayloadIsRejectedBeforeUpload() {
        var service = new CloudinaryService(null);
        var file = new MockMultipartFile("file", "payload.txt", "text/plain", "not an image".getBytes());

        assertThatThrownBy(() -> service.uploadImage(file, "evidence"))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_EVIDENCE_FILE);
    }
}
