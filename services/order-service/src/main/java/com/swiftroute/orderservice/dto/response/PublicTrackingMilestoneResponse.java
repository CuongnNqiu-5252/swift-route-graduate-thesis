package com.swiftroute.orderservice.dto.response;

import java.time.Instant;

/**
 * Customer-safe milestone entry displayed on the public tracking page.
 */
public record PublicTrackingMilestoneResponse(
        String id,
        String eventType,
        String title,
        String description,
        String status,
        String proofUrl,
        Instant occurredAt) {
}
