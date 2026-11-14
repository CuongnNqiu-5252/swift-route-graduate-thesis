package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.orderservice.entity.PackageHandover;

public record PackageHandoverResponse(
        UUID handoverId,
        UUID fromDriverId,
        UUID toDriverId,
        String status,
        String reason,
        UUID assignedBy,
        Instant assignedAt,
        UUID rejectedBy,
        Instant rejectedAt,
        UUID cancelledBy,
        Instant cancelledAt,
        String adminNotes,
        String receiverNotes,
        Instant completedAt,
        Instant createdAt,
        UUID orderId,
        String orderCode) {
    public static PackageHandoverResponse from(PackageHandover handover) {
        return new PackageHandoverResponse(
                handover.getId(),
                handover.getFromDriverId(),
                handover.getToDriverId(),
                handover.getStatus(),
                handover.getReason(),
                handover.getAssignedBy(),
                handover.getAssignedAt(),
                handover.getRejectedBy(),
                handover.getRejectedAt(),
                handover.getCancelledBy(),
                handover.getCancelledAt(),
                handover.getAdminNotes(),
                handover.getReceiverNotes(),
                handover.getCompletedAt(),
                handover.getCreatedAt(),
                handover.getOrder().getId(),
                handover.getOrder().getExternalOrderCode());
    }
}
