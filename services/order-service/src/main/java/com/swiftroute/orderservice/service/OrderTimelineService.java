package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.response.OrderTimelineEventResponse;
import com.swiftroute.orderservice.dto.response.OrderTimelineResponse;
import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.DeliveryIncident;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OrderStatusHistory;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.DeliveryIncidentRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OrderStatusHistoryRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * Aggregates status history, delivery attempts, incidents,
 * return requests, and COD collection milestones into a chronological order timeline.
 */
@Service
@RequiredArgsConstructor
public class OrderTimelineService {

    private static final List<String> PRIVILEGED_ROLES = List.of(
            "SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR");

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryIncidentRepository deliveryIncidentRepository;
    private final ReturnRequestRepository returnRequestRepository;

    /**
     * Loads the chronological activity timeline for an order after verifying tenant and role access.
     */
    @Transactional(readOnly = true)
    public OrderTimelineResponse getOrderTimeline(
            UUID orderId,
            UUID requesterId,
            String requesterRole,
            UUID tenantIdFromContext) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        TenantRequestInterceptor.assertRequestTenant(order.getTenant().getId());
        assertOrderAccess(order, requesterId, requesterRole, tenantIdFromContext);

        List<OrderTimelineEventResponse> events = new ArrayList<>();
        addStatusEvents(order, events);
        String latestProofUrl = addDeliveryAttemptEvents(orderId, events);
        addIncidentEvents(orderId, events);
        addReturnEvents(orderId, events);
        addCodEvent(order, events);

        events.sort(Comparator
                .comparing(OrderTimelineEventResponse::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(OrderTimelineEventResponse::id));

        return new OrderTimelineResponse(
                order.getId(),
                order.getExternalOrderCode() != null && !order.getExternalOrderCode().isBlank()
                        ? order.getExternalOrderCode()
                        : order.getId().toString(),
                order.getTrackingToken(),
                order.getStatus(),
                order.getAssignedDriverId(),
                order.getPlanId(),
                latestProofUrl,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                List.copyOf(events));
    }

    private void assertOrderAccess(Order order, UUID requesterId, String requesterRole, UUID tenantIdFromContext) {
        if ("DRIVER".equalsIgnoreCase(requesterRole)) {
            boolean isAssignedDriver = order.getAssignedDriverId() != null
                    && order.getAssignedDriverId().equals(requesterId);
            if (!isAssignedDriver) {
                throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
            }
            return;
        }
        if (requesterRole != null && PRIVILEGED_ROLES.contains(requesterRole)) {
            return;
        }
        boolean isTenantMember = tenantIdFromContext != null
                && order.getTenant().getId().equals(tenantIdFromContext);
        boolean isTenantOwner = order.getTenant().getOwnerUserId() != null
                && order.getTenant().getOwnerUserId().equals(requesterId);
        if (!isTenantMember && !isTenantOwner) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private void addStatusEvents(Order order, List<OrderTimelineEventResponse> events) {
        List<OrderStatusHistory> history = statusHistoryRepository.findAllByOrder_IdOrderByCreatedAtDesc(order.getId());
        if (!history.isEmpty()) {
            for (OrderStatusHistory entry : history) {
                events.add(new OrderTimelineEventResponse(
                        "status-" + entry.getId(),
                        entry.getFromStatus() == null ? "ORDER_CREATED" : "ORDER_STATUS_CHANGED",
                        "STATUS",
                        statusTitle(entry.getFromStatus(), entry.getToStatus()),
                        entry.getReason(),
                        entry.getToStatus(),
                        entry.getFromStatus(),
                        entry.getToStatus(),
                        entry.getChangedByUserId(),
                        entry.getChangedByRole(),
                        null,
                        List.of(),
                        entry.getCreatedAt(),
                        Map.of()));
            }
            return;
        }

        // Legacy orders created before status history recording get a clean baseline milestone.
        events.add(new OrderTimelineEventResponse(
                "status-created-" + order.getId(),
                "ORDER_CREATED",
                "STATUS",
                "Tạo đơn hàng",
                "Đơn hàng được khởi tạo",
                OrderStatus.READY_FOR_DISPATCH.name(),
                null,
                OrderStatus.READY_FOR_DISPATCH.name(),
                null,
                null,
                null,
                List.of(),
                order.getCreatedAt(),
                Map.of()));

        if (order.getStatus() != null && order.getStatus() != OrderStatus.READY_FOR_DISPATCH) {
            events.add(new OrderTimelineEventResponse(
                    "status-current-" + order.getId(),
                    "ORDER_STATUS_CHANGED",
                    "STATUS",
                    statusTitle(null, order.getStatus().name()),
                    "Trạng thái hiện tại của đơn hàng",
                    order.getStatus().name(),
                    OrderStatus.READY_FOR_DISPATCH.name(),
                    order.getStatus().name(),
                    null,
                    null,
                    null,
                    List.of(),
                    order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt(),
                    Map.of()));
        }
    }

    private String addDeliveryAttemptEvents(UUID orderId, List<OrderTimelineEventResponse> events) {
        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findAllByOrder_IdOrderByCompletedAtDesc(orderId);
        String latestProofUrl = null;
        for (DeliveryAttempt attempt : attempts) {
            if (latestProofUrl == null && isNonBlank(attempt.getProofUrl())) {
                latestProofUrl = attempt.getProofUrl().trim();
            }
            boolean success = "SUCCESS".equalsIgnoreCase(attempt.getOutcome());
            int number = attempt.getAttemptNumber() != null ? attempt.getAttemptNumber() : 1;
            String title = (success ? "Giao hàng thành công" : "Giao hàng thất bại") + " (Lần " + number + ")";

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("attempt_number", number);
            metadata.put("outcome", attempt.getOutcome());
            if (isNonBlank(attempt.getFailureReason())) {
                metadata.put("failure_reason", attempt.getFailureReason());
            }
            if (attempt.getContactAttempts() != null) {
                metadata.put("contact_attempts", attempt.getContactAttempts());
            }

            events.add(new OrderTimelineEventResponse(
                    "attempt-" + attempt.getId(),
                    success ? "DELIVERY_ATTEMPT_SUCCESS" : "DELIVERY_ATTEMPT_FAILED",
                    "DELIVERY",
                    title,
                    joinParts(attempt.getFailureReason(), attempt.getNotes()),
                    attempt.getOutcome(),
                    null,
                    null,
                    attempt.getCompletedBy(),
                    "DRIVER",
                    trimToNull(attempt.getProofUrl()),
                    List.of(),
                    attempt.getCompletedAt(),
                    metadata));
        }
        return latestProofUrl;
    }

    private void addIncidentEvents(UUID orderId, List<OrderTimelineEventResponse> events) {
        for (DeliveryIncident incident : deliveryIncidentRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)) {
            List<String> evidence = parseEvidenceUrls(incident.getEvidenceUrls());
            String actorRole = incident.getDriverId() != null && incident.getDriverId().equals(incident.getReportedBy())
                    ? "DRIVER"
                    : "OPERATOR";

            Map<String, Object> reportMeta = new LinkedHashMap<>();
            reportMeta.put("incident_type", incident.getIncidentType());
            reportMeta.put("severity", incident.getSeverity());
            reportMeta.put("incident_status", incident.getStatus());

            events.add(new OrderTimelineEventResponse(
                    "incident-open-" + incident.getId(),
                    "INCIDENT_REPORTED",
                    "INCIDENT",
                    "Báo cáo sự cố: " + incident.getIncidentType(),
                    trimToNull(incident.getNotes()),
                    incident.getStatus(),
                    null,
                    null,
                    incident.getReportedBy(),
                    actorRole,
                    null,
                    evidence,
                    incident.getCreatedAt(),
                    reportMeta));

            if (incident.getResolvedAt() != null) {
                Map<String, Object> resolveMeta = new LinkedHashMap<>();
                resolveMeta.put("incident_type", incident.getIncidentType());
                if (isNonBlank(incident.getResolutionType())) {
                    resolveMeta.put("resolution_type", incident.getResolutionType());
                }
                events.add(new OrderTimelineEventResponse(
                        "incident-resolved-" + incident.getId(),
                        "INCIDENT_RESOLVED",
                        "INCIDENT",
                        "Đã xử lý sự cố",
                        joinParts(incident.getResolutionType(), incident.getResolutionNotes()),
                        incident.getStatus(),
                        null,
                        null,
                        incident.getResolvedBy(),
                        "OPERATOR",
                        null,
                        List.of(),
                        incident.getResolvedAt(),
                        resolveMeta));
            }
        }
    }

    private void addReturnEvents(UUID orderId, List<OrderTimelineEventResponse> events) {
        for (ReturnRequest ret : returnRequestRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId)) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("return_status", ret.getStatus());

            events.add(new OrderTimelineEventResponse(
                    "return-req-" + ret.getId(),
                    "RETURN_REQUESTED",
                    "RETURN",
                    "Tạo yêu cầu hoàn hàng về kho",
                    trimToNull(ret.getReason()),
                    ret.getStatus(),
                    null,
                    null,
                    ret.getRequestedBy(),
                    null,
                    null,
                    List.of(),
                    ret.getCreatedAt(),
                    meta));

            if ("DISCREPANCY".equals(ret.getStatus()) && ret.getReceivedAt() == null) {
                Map<String, Object> discMeta = new LinkedHashMap<>(meta);
                if (isNonBlank(ret.getDiscrepancyType())) {
                    discMeta.put("discrepancy_type", ret.getDiscrepancyType());
                }
                List<String> evidence = isNonBlank(ret.getDiscrepancyEvidenceUrl())
                        ? List.of(ret.getDiscrepancyEvidenceUrl().trim())
                        : List.of();
                events.add(new OrderTimelineEventResponse(
                        "return-discrepancy-" + ret.getId(),
                        "RETURN_DISCREPANCY",
                        "RETURN",
                        "Kho ghi nhận chênh lệch / hư hỏng khi hoàn",
                        trimToNull(ret.getDiscrepancyNotes()),
                        ret.getStatus(),
                        null,
                        null,
                        null,
                        "OPERATOR",
                        null,
                        evidence,
                        ret.getUpdatedAt() != null ? ret.getUpdatedAt() : ret.getCreatedAt(),
                        discMeta));
            }

            if (ret.getReceivedAt() != null) {
                Map<String, Object> receiptMeta = new LinkedHashMap<>(meta);
                if (isNonBlank(ret.getDiscrepancyType())) {
                    receiptMeta.put("discrepancy_type", ret.getDiscrepancyType());
                }
                List<String> evidence = isNonBlank(ret.getDiscrepancyEvidenceUrl())
                        ? List.of(ret.getDiscrepancyEvidenceUrl().trim())
                        : List.of();
                String title = isNonBlank(ret.getDiscrepancyType())
                        ? "Kho nhận hàng hoàn (Có chênh lệch)"
                        : "Kho đã kiểm nhận hàng hoàn";
                events.add(new OrderTimelineEventResponse(
                        "return-received-" + ret.getId(),
                        "RETURN_RECEIVED",
                        "RETURN",
                        title,
                        joinParts(ret.getDiscrepancyNotes(), ret.getCompletionNotes()),
                        ret.getStatus(),
                        null,
                        null,
                        ret.getReceivedBy(),
                        "OPERATOR",
                        null,
                        evidence,
                        ret.getReceivedAt(),
                        receiptMeta));
            }
        }
    }

    private void addCodEvent(Order order, List<OrderTimelineEventResponse> events) {
        if (order.getCodCollectedAt() == null) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cod_amount", order.getCodAmount());
        if (isNonBlank(order.getCodStatus())) {
            meta.put("cod_status", order.getCodStatus());
        }
        events.add(new OrderTimelineEventResponse(
                "cod-collected-" + order.getId(),
                "COD_COLLECTED",
                "COD",
                "Đã thu tiền thu hộ (COD)",
                order.getCodAmount() != null ? "Số tiền thu hộ: " + order.getCodAmount().toPlainString() + " VND" : null,
                order.getCodStatus(),
                null,
                null,
                order.getAssignedDriverId(),
                "DRIVER",
                null,
                List.of(),
                order.getCodCollectedAt(),
                meta));
    }

    private static String statusTitle(String fromStatus, String toStatus) {
        if (fromStatus == null && OrderStatus.READY_FOR_DISPATCH.name().equals(toStatus)) {
            return "Tạo đơn hàng";
        }
        if (toStatus == null) {
            return "Cập nhật trạng thái";
        }
        return switch (toStatus) {
            case "READY_FOR_DISPATCH" -> "Đưa vào hàng chờ điều phối";
            case "PLANNED" -> "Đã lên lịch giao hàng";
            case "IN_TRANSIT" -> "Đang giao hàng";
            case "DELIVERED" -> "Giao hàng thành công";
            case "FAILED" -> "Giao hàng thất bại / Sự cố";
            case "RETURNING" -> "Đang hoàn hàng về kho";
            case "RETURNED" -> "Đã hoàn hàng về kho";
            case "CANCELLED" -> "Đã huỷ đơn hàng";
            default -> "Chuyển trạng thái: " + toStatus;
        };
    }

    private static List<String> parseEvidenceUrls(String raw) {
        if (!isNonBlank(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String joinParts(String first, String second) {
        String a = trimToNull(first);
        String b = trimToNull(second);
        if (a != null && b != null && !a.equalsIgnoreCase(b)) {
            return a + " — " + b;
        }
        return a != null ? a : b;
    }

    private static String trimToNull(String value) {
        return isNonBlank(value) ? value.trim() : null;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
