package com.swiftroute.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped work awaiting an operations decision. */
public record OperationsReviewResponse(
        List<IncidentItem> incidents,
        List<ReturnItem> returns,
        List<CodItem> codCollections) {

    public record IncidentItem(
            UUID id,
            UUID orderId,
            String orderCode,
            UUID dispatchPlanId,
            UUID driverId,
            String incidentType,
            String severity,
            String status,
            String notes,
            boolean canContinueDelivery,
            boolean needsImmediateAssistance,
            Instant reportedAt) {

        public IncidentItem(UUID id, UUID orderId, String orderCode, String incidentType, String notes, Instant reportedAt) {
            this(id, orderId, orderCode, null, null, incidentType, "MEDIUM", "OPEN", notes, true, false, reportedAt);
        }
    }

    public record ReturnItem(
            UUID id,
            UUID orderId,
            String orderCode,
            String requestType,
            String reason,
            String status,
            UUID driverId,
            Instant requestedAt) {}

    public record CodItem(
            UUID id,
            UUID driverId,
            BigDecimal ledgerTotal,
            BigDecimal declaredAmount,
            int orderCount,
            List<String> orderCodes,
            String notes,
            Instant declaredAt) {}
}
