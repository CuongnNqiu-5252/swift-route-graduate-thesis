package com.swiftroute.orderservice.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderTrackingLinkResponse(
        UUID orderId,
        String trackingUrl,
        OffsetDateTime expiresAt) {
}
