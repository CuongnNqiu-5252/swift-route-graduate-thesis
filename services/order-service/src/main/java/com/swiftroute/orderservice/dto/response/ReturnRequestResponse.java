package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.orderservice.entity.ReturnRequest;

public record ReturnRequestResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        UUID incidentId,
        String requestType,
        String reason,
        String status,
        UUID returnLocationId,
        String returnLocationName,
        String returnAddress,
        UUID currentHolderId,
        int packageCount,
        UUID requestedBy,
        UUID approvedBy,
        UUID returnTaskId,
        UUID receivedBy,
        Instant receivedAt,
        String completionNotes,
        String discrepancyType,
        Integer actualQuantity,
        String packageCondition,
        String discrepancyNotes,
        String discrepancyEvidenceUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReturnRequestResponse from(ReturnRequest req) {
        if (req == null) return null;

        UUID orderId = req.getOrder() != null ? req.getOrder().getId() : null;
        String orderCode = req.getOrder() != null ? req.getOrder().getExternalOrderCode() : null;
        UUID incidentId = req.getIncident() != null ? req.getIncident().getId() : null;
        UUID returnLocId = req.getReturnLocation() != null ? req.getReturnLocation().getId() : null;
        String returnLocName = req.getReturnLocation() != null ? req.getReturnLocation().getName() : null;
        String returnAddress = req.getReturnLocation() != null ? req.getReturnLocation().getAddress() : null;
        UUID taskId = req.getReturnTask() != null ? req.getReturnTask().getId() : null;

        return new ReturnRequestResponse(
                req.getId(),
                orderId,
                orderCode,
                incidentId,
                req.getRequestType(),
                req.getReason(),
                req.getStatus(),
                returnLocId,
                returnLocName,
                returnAddress,
                req.getCurrentHolderId(),
                req.getPackageCount() != null ? req.getPackageCount() : 1,
                req.getRequestedBy(),
                req.getApprovedBy(),
                taskId,
                req.getReceivedBy(),
                req.getReceivedAt(),
                req.getCompletionNotes(),
                req.getDiscrepancyType(),
                req.getActualQuantity(),
                req.getPackageCondition(),
                req.getDiscrepancyNotes(),
                req.getDiscrepancyEvidenceUrl(),
                req.getCreatedAt(),
                req.getUpdatedAt()
        );
    }
}

