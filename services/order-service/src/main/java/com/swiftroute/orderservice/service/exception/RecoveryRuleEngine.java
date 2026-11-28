package com.swiftroute.orderservice.service.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.entity.DeliveryIncident;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.service.OrderStatusHistoryRecorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Automated recovery rule engine for failed delivery stops.
 *
 * <ul>
 *   <li>{@code IMMEDIATE_RETURN_TYPES} (customer refused, damaged package, cancelled by sender)
 *       and stops exceeding {@code DEFAULT_MAX_DELIVERY_ATTEMPTS} automatically transition the
 *       order directly to {@code RETURNING} and resolve the incident ({@code AUTO_RETURN}).</li>
 *   <li>Retryable stop failures ({@code CUSTOMER_UNREACHABLE}, {@code WRONG_ADDRESS}) remain
 *       {@code FAILED} with an {@code OPEN} incident so dispatchers can retry or reschedule.</li>
 * </ul>
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
            "WRONG_ADDRESS");

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final OutboxEventRepository outboxEventRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OrderStatusHistoryRecorder statusHistory;

    @Transactional
    public String evaluateAndApply(DeliveryIncident incident) {
        String normalizedType = incident.getIncidentType() != null
                ? incident.getIncidentType().trim().toUpperCase(Locale.ROOT)
                : "OTHER";

        Order order = incident.getOrder();
        if (order == null) {
            publishIncidentAlert(incident, "MANUAL_REVIEW", 0);
            return "MANUAL_REVIEW";
        }

        if ("OFFLINE_SYNC_CONFLICT".equals(normalizedType)) {
            publishIncidentAlert(incident, "MANUAL_REVIEW", 0);
            return "MANUAL_REVIEW";
        }

        UUID orderId = order.getId();
        int attempts = deliveryAttemptRepository.maxAttemptNumber(orderId);
        String recoveryAction = determineRecoveryAction(normalizedType, attempts);

        if ("RETURN_TO_ORIGIN".equals(recoveryAction)) {
            boolean immediateReturn = IMMEDIATE_RETURN_TYPES.contains(normalizedType);
            String historyReason = immediateReturn
                    ? "Tự động chuyển hoàn về kho (" + friendlyImmediateReason(normalizedType) + ")"
                    : "Tự động chuyển hoàn về kho sau " + attempts + " lần giao thất bại";

            OrderStatus previous = order.getStatus();
            if (previous != OrderStatus.RETURNING && previous != OrderStatus.RETURNED) {
                order.setStatus(OrderStatus.RETURNING);
                order.setUpdatedAt(Instant.now());
                if (statusHistory != null) {
                    statusHistory.record(
                            order,
                            previous,
                            OrderStatus.RETURNING,
                            incident.getReportedBy(),
                            "SYSTEM",
                            historyReason);
                }
            }
            incident.setStatus("RESOLVED");
            incident.setResolutionType("AUTO_RETURN");
            incident.setResolvedBy(incident.getReportedBy());
            incident.setResolvedAt(Instant.now());
            incident.setResolutionNotes(historyReason);
            log.info("Order {} transitioned directly to RETURNING (reason={}, attempts={}).",
                    orderId, normalizedType, attempts);
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

    private static String friendlyImmediateReason(String normalizedType) {
        return switch (normalizedType) {
            case "REFUSED", "CUSTOMER_REFUSED", "REFUSED_BY_RECIPIENT" -> "Khách từ chối nhận hàng";
            case "DAMAGED", "PACKAGE_DAMAGED", "GOODS_DAMAGED" -> "Kiện hàng bị hư hỏng";
            case "CANCELLED_BY_SENDER" -> "Người gửi yêu cầu huỷ / hoàn";
            default -> normalizedType;
        };
    }

    private void publishIncidentAlert(DeliveryIncident incident, String recoveryAction, int attempts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incident_id", incident.getId() != null ? incident.getId().toString() : null);
        payload.put("order_id", incident.getOrder() != null ? incident.getOrder().getId().toString() : null);
        payload.put("dispatch_plan_id",
                incident.getDispatchPlan() != null ? incident.getDispatchPlan().getId().toString() : null);
        payload.put("tenant_id", incident.getTenantId().toString());
        payload.put("driver_id", incident.getDriverId() != null ? incident.getDriverId().toString() : null);
        payload.put("reported_by", incident.getReportedBy().toString());
        payload.put("action_url", "RETURN_TO_ORIGIN".equals(recoveryAction)
                ? "/operations"
                : "/operations?incident=" + incident.getId());
        payload.put("incident_type", incident.getIncidentType());
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
