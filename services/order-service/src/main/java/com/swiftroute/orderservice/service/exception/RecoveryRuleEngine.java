package com.swiftroute.orderservice.service.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.entity.DeliveryIncident;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Automated recovery rule engine for delivery incidents (P2.6).
 *
 * <p>Determines whether a failed delivery or reported incident should be
 * recommended for redelivery (`REDELIVERY`), return to origin (`RETURN_TO_ORIGIN`),
 * or operator review (`MANUAL_REVIEW`) based on incident category and attempt count.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecoveryRuleEngine {

    public static final int DEFAULT_MAX_DELIVERY_ATTEMPTS = 3;

    private static final Set<String> IMMEDIATE_RETURN_TYPES = Set.of(
            "DAMAGED",
            "PACKAGE_DAMAGED",
            "GOODS_DAMAGED",
            "REFUSED",
            "CUSTOMER_REFUSED",
            "REFUSED_BY_RECIPIENT",
            "CANCELLED_BY_SENDER");

    private static final Set<String> RETRYABLE_TYPES = Set.of(
            "CUSTOMER_ABSENT",
            "CUSTOMER_UNREACHABLE",
            "RECIPIENT_UNAVAILABLE",
            "ACCESS_DENIED",
            "WRONG_ADDRESS",
            "WEATHER_DELAY");

    private static final List<String> UNFINISHED_RETURN_STATUSES = List.of(
            "REQUESTED", "APPROVED", "ASSIGNED", "IN_EXECUTION", "ARRIVED", "AWAITING_RECEIPT");

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Evaluates automated recovery rules for a newly reported delivery incident.
     *
     * @param incident the persisted delivery incident
     * @return the recommended recovery action (`RETURN_TO_ORIGIN`, `REDELIVERY`, `CONTINUE_DELIVERY`, or `MANUAL_REVIEW`)
     */
    @Transactional
    public String evaluateAndApply(DeliveryIncident incident) {
        String normalizedType = incident.getIncidentType() != null
                ? incident.getIncidentType().trim().toUpperCase(Locale.ROOT)
                : "OTHER";

        // Route-level incident (no specific order attached, e.g., vehicle breakdown)
        Order order = incident.getOrder();
        if (order == null) {
            publishIncidentAlert(incident, "MANUAL_REVIEW", 0);
            return "MANUAL_REVIEW";
        }

        // A sync conflict records competing field/server facts for an operator.
        // It must never create a delivery attempt, redelivery, or return request.
        if ("OFFLINE_SYNC_CONFLICT".equals(normalizedType)) {
            publishIncidentAlert(incident, "MANUAL_REVIEW", 0);
            return "MANUAL_REVIEW";
        }

        // Non-failing operational incident where order delivery itself was not marked FAILED
        if (order.getStatus() != OrderStatus.FAILED && Boolean.TRUE.equals(incident.getCanContinueDelivery())) {
            publishIncidentAlert(incident, "CONTINUE_DELIVERY", 0);
            return "CONTINUE_DELIVERY";
        }

        UUID orderId = order.getId();
        int attempts = deliveryAttemptRepository.maxAttemptNumber(orderId);
        String recoveryAction = determineRecoveryAction(normalizedType, attempts);

        // Only auto-create a ReturnRequest when maximum delivery attempts have been exhausted
        boolean hasOpenReturn = returnRequestRepository
                .existsByOrder_IdAndStatusIn(orderId, UNFINISHED_RETURN_STATUSES);

        if (!hasOpenReturn && attempts >= DEFAULT_MAX_DELIVERY_ATTEMPTS) {
            ReturnRequest autoRequest = ReturnRequest.builder()
                    .order(order)
                    .incident(incident)
                    .tenantId(incident.getTenantId())
                    .returnLocation(order.getPickupLocation())
                    .currentHolderId(order.getAssignedDriverId())
                    .requestedBy(incident.getReportedBy())
                    .requestType("RETURN_TO_ORIGIN")
                    .reason("Tự động yêu cầu hoàn hàng sau khi vượt quá số lần giao tối đa (" + attempts + " lần)")
                    .status("REQUESTED")
                    .build();
            returnRequestRepository.save(autoRequest);
            incident.setStatus("IN_PROGRESS");
            incident.setResolutionType("REQUEST_RETURN");
            log.info("Order {} max attempts ({}) reached; auto-created ReturnRequest RETURN_TO_ORIGIN.",
                    orderId, attempts);
        }

        publishIncidentAlert(incident, recoveryAction, attempts);
        return recoveryAction;
    }

    public String determineRecoveryAction(String normalizedIncidentType, int attemptsSoFar) {
        if (IMMEDIATE_RETURN_TYPES.contains(normalizedIncidentType)) {
            return "RETURN_TO_ORIGIN";
        }
        if (attemptsSoFar >= DEFAULT_MAX_DELIVERY_ATTEMPTS) {
            return "RETURN_TO_ORIGIN";
        }
        if (RETRYABLE_TYPES.contains(normalizedIncidentType)) {
            return "REDELIVERY";
        }
        return "MANUAL_REVIEW";
    }

    private void publishIncidentAlert(DeliveryIncident incident, String recoveryAction, int attempts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incident_id", incident.getId() != null ? incident.getId().toString() : null);
        payload.put("order_id", incident.getOrder() != null ? incident.getOrder().getId().toString() : null);
        payload.put("dispatch_plan_id", incident.getDispatchPlan() != null ? incident.getDispatchPlan().getId().toString() : null);
        payload.put("tenant_id", incident.getTenantId().toString());
        payload.put("driver_id", incident.getDriverId() != null ? incident.getDriverId().toString() : null);
        payload.put("reported_by", incident.getReportedBy().toString());
        payload.put("action_url", "/operations?incident=" + incident.getId());
        payload.put("incident_type", incident.getIncidentType());
        payload.put("severity", incident.getSeverity());
        payload.put("notes", incident.getNotes());
        payload.put("attempt_count", attempts);
        payload.put("recovery_action", recoveryAction);

        UUID aggregateId = incident.getId() != null
                ? incident.getId()
                : (incident.getOrder() != null ? incident.getOrder().getId() : incident.getTenantId());

        outboxEventRepository.save(OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("DeliveryIncident")
                .aggregateId(aggregateId)
                .eventType("delivery.incident")
                .payload(payload)
                .build());
    }
}
