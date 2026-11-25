package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single event entry inside an order's unified activity timeline.
 */
public record OrderTimelineEventResponse(
        String id,
        String eventType,
        String category,
        String title,
        String description,
        String status,
        String fromStatus,
        String toStatus,
        UUID actorId,
        String actorRole,
        String proofUrl,
        List<String> evidenceUrls,
        Instant occurredAt,
        Map<String, Object> metadata) {
}
