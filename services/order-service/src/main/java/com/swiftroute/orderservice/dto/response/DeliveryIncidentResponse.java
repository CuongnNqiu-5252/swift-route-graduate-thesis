package com.swiftroute.orderservice.dto.response;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.swiftroute.orderservice.entity.DeliveryIncident;

public record DeliveryIncidentResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        UUID dispatchPlanId,
        UUID driverId,
        UUID reportedBy,
        String incidentType,
        String severity,
        String notes,
        List<String> evidenceUrls,
        boolean canContinueDelivery,
        boolean needsImmediateAssistance,
        String status,
        UUID assignedOperatorId,
        String resolutionType,
        UUID resolvedBy,
        String resolutionNotes,
        Instant createdAt,
        Instant resolvedAt
) {
    public static DeliveryIncidentResponse from(DeliveryIncident incident) {
        if (incident == null) return null;

        List<String> urls = Collections.emptyList();
        if (incident.getEvidenceUrls() != null && !incident.getEvidenceUrls().isBlank()) {
            urls = Arrays.stream(incident.getEvidenceUrls().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        UUID orderId = incident.getOrder() != null ? incident.getOrder().getId() : null;
        String orderCode = incident.getOrder() != null ? incident.getOrder().getExternalOrderCode() : null;
        UUID planId = incident.getDispatchPlan() != null ? incident.getDispatchPlan().getId() : null;

        return new DeliveryIncidentResponse(
                incident.getId(),
                orderId,
                orderCode,
                planId,
                incident.getDriverId(),
                incident.getReportedBy(),
                incident.getIncidentType(),
                incident.getSeverity(),
                incident.getNotes(),
                urls,
                incident.getCanContinueDelivery() != null && incident.getCanContinueDelivery(),
                incident.getNeedsImmediateAssistance() != null && incident.getNeedsImmediateAssistance(),
                incident.getStatus(),
                incident.getAssignedOperatorId(),
                incident.getResolutionType(),
                incident.getResolvedBy(),
                incident.getResolutionNotes(),
                incident.getCreatedAt(),
                incident.getResolvedAt()
        );
    }
}

