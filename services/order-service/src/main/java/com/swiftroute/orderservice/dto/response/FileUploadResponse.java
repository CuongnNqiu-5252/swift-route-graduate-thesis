package com.swiftroute.orderservice.dto.response;

import java.time.Instant;

public record FileUploadResponse(
        String url,
        String publicId,
        String format,
        long bytes,
        String originalFilename,
        Instant uploadedAt
) {}

