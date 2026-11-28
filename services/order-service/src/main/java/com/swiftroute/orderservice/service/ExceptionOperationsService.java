package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.CodHandoverRequest;
import com.swiftroute.orderservice.dto.request.CollectCodRequest;
import com.swiftroute.orderservice.dto.request.CreateIncidentRequest;
import com.swiftroute.orderservice.dto.request.RecordCodCollectionRequest;
import com.swiftroute.orderservice.dto.request.ResolveIncidentRequest;
import com.swiftroute.orderservice.dto.request.SubmitCodHandoverRequest;
import com.swiftroute.orderservice.dto.response.CodCollectionResponse;
import com.swiftroute.orderservice.dto.response.CodHandoverResponse;
import com.swiftroute.orderservice.dto.response.DeliveryIncidentResponse;
import com.swiftroute.orderservice.dto.response.DriverCodCollectionResponse;
import com.swiftroute.orderservice.dto.response.OperationsReviewResponse;
import com.swiftroute.orderservice.dto.response.PageRequests;
import com.swiftroute.orderservice.dto.response.PageResponse;
import com.swiftroute.orderservice.dto.response.ReturningOrderResponse;
import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.DeliveryIncident;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.DeliveryIncidentRepository;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.service.exception.RecoveryRuleEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExceptionOperationsService {

    private static final Set<String> OPERATOR_ROLES = Set.of(
            "SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR", "WAREHOUSE_STAFF",
            "TENANT_OWNER", "MANAGER", "STAFF");

    private static final List<String> UNFINISHED_INCIDENT_STATUSES = List.of(
            "OPEN", "IN_PROGRESS");

    private final OrderRepository orders;
    private final DeliveryIncidentRepository incidents;
    private final DeliveryExecutionService execution;
    private final RecoveryRuleEngine recoveryRuleEngine;
    private final DispatchPlanRepository dispatchPlans;
    private final DeliveryAttemptRepository deliveryAttempts;
    private final OutboxEventRepository outboxEvents;
    private final CodLedgerService codLedger;
    private final RecipientDeliveryEvents recipientDeliveryEvents;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OrderStatusHistoryRecorder statusHistory;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.swiftroute.orderservice.service.planning.DispatchOrchestrationService dispatchOrchestration;

    // =========================================================================
    // 1. FAILED DELIVERY & EXCEPTION MANAGEMENT
    // =========================================================================

    @Transactional
    public DeliveryIncidentResponse reportIncident(
            CreateIncidentRequest request,
            UUID tenantId,
            UUID reporterId,
            String role) {

        boolean isDriver = "DRIVER".equalsIgnoreCase(role);
        boolean isOperator = isOperator(role);

        if (!isDriver && !isOperator) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Order order = null;
        DispatchPlan plan = null;
        UUID driverId = null;

        if (request.orderId() != null) {
            order = owned(request.orderId(), tenantId);
            driverId = order.getAssignedDriverId();
            if (isDriver && (driverId == null || !driverId.equals(reporterId))) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
        }

        if (request.dispatchPlanId() != null) {
            plan = dispatchPlans.findById(request.dispatchPlanId())
                    .filter(p -> p.getTenant().getId().equals(tenantId))
                    .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
            if (driverId == null) {
                driverId = plan.getDriverId();
            }
            if (isDriver && (plan.getDriverId() == null || !plan.getDriverId().equals(reporterId))) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
        }

        if (order == null && plan == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        if (isDriver && driverId == null) {
            driverId = reporterId;
        }

        String normalizedType = request.incidentType() != null
                ? request.incidentType().trim().toUpperCase(Locale.ROOT)
                : "OTHER";
        boolean auditOnlyIncident = "OFFLINE_SYNC_CONFLICT".equals(normalizedType);

        if (!auditOnlyIncident && order != null
                && incidents.existsByOrder_IdAndStatusIn(order.getId(), UNFINISHED_INCIDENT_STATUSES)) {
            var existingList = incidents.findAllByOrder_IdOrderByCreatedAtDesc(order.getId());
            if (!existingList.isEmpty()) {
                DeliveryIncident existing = existingList.get(0);
                log.info("Unfinished incident already exists for order {}, returning existing incident {}",
                        order.getId(), existing.getId());
                return DeliveryIncidentResponse.from(existing);
            }
        }

        if (order != null && !auditOnlyIncident) {
            String photoProof = (request.evidenceUrls() != null && !request.evidenceUrls().isEmpty())
                    ? request.evidenceUrls().get(0)
                    : null;

            DeliveryTask failedTask = execution.markDeliveryTaskFailed(order, driverId);
            int nextAttemptNumber = deliveryAttempts.maxAttemptNumber(order.getId()) + 1;

            DeliveryAttempt attempt = DeliveryAttempt.builder()
                    .order(order)
                    .task(failedTask)
                    .tenantId(tenantId)
                    .attemptNumber(nextAttemptNumber)
                    .outcome("FAILED")
                    .failureReason(normalizedType)
                    .notes(request.notes())
                    .proofUrl(photoProof)
                    .completedBy(reporterId)
                    .build();
            deliveryAttempts.save(attempt);

            if (order.getStatus() != OrderStatus.RETURNING) {
                OrderStatus previous = order.getStatus();
                order.setStatus(OrderStatus.FAILED);
                recordStatusChange(order, previous, OrderStatus.FAILED, reporterId, role,
                        joinReason(normalizedType, request.notes()));
                recipientDeliveryEvents.recordResult(attempt);
            }
            order.setUpdatedAt(Instant.now());
            execution.completeDeliveryPlanIfFinished(order);
        }

        String evidenceUrlsStr = (request.evidenceUrls() != null && !request.evidenceUrls().isEmpty())
                ? String.join(",", request.evidenceUrls())
                : null;

        DeliveryIncident incident = DeliveryIncident.builder()
                .order(order)
                .dispatchPlan(plan)
                .tenantId(tenantId)
                .reportedBy(reporterId)
                .driverId(driverId)
                .incidentType(normalizedType)
                .notes(request.notes())
                .evidenceUrls(evidenceUrlsStr)
                .status("OPEN")
                .build();

        DeliveryIncident saved = incidents.save(incident);
        recoveryRuleEngine.evaluateAndApply(saved);
        incidents.save(saved);
        if (order != null) {
            orders.save(order);
        }

        log.info("Delivery incident reported: id={}, type={}, status={}, order={}",
                saved.getId(), saved.getIncidentType(), saved.getStatus(),
                order != null ? order.getId() : "NONE");

        return DeliveryIncidentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryIncidentResponse> getIncidentPage(
            UUID tenantId, String status, UUID driverId, UUID orderId, int page, int size) {
        return PageResponse.fromPage(incidents.findPage(
                tenantId,
                (status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT)),
                driverId,
                orderId,
                PageRequests.newestFirst(page, size, "createdAt")).map(DeliveryIncidentResponse::from));
    }

    @Transactional(readOnly = true)
    public List<DeliveryIncidentResponse> getIncidents(
            UUID tenantId,
            String status,
            UUID driverId,
            UUID orderId) {
        List<DeliveryIncident> list = incidents.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        return list.stream()
                .filter(i -> status == null || status.isBlank() || i.getStatus().equalsIgnoreCase(status))
                .filter(i -> driverId == null || driverId.equals(i.getDriverId()))
                .filter(i -> orderId == null || (i.getOrder() != null && orderId.equals(i.getOrder().getId())))
                .map(DeliveryIncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeliveryIncidentResponse getIncidentById(UUID id, UUID tenantId) {
        DeliveryIncident incident = incidents.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return DeliveryIncidentResponse.from(incident);
    }

    @Transactional
    public DeliveryIncidentResponse resolveIncident(
            UUID id,
            UUID tenantId,
            UUID actorId,
            String role,
            ResolveIncidentRequest request) {

        requireOperator(role);

        DeliveryIncident incident = incidents.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        String resType = request.resolutionType().toUpperCase(Locale.ROOT);
        incident.setResolutionType(resType);
        incident.setResolutionNotes(request.notes());
        // DispatchPlan plan = DispatchPlanRepository
        Order order = incident.getOrder();

        switch (resType) {
            case "RETRY_DELIVERY" -> {
                if (order != null) {
                    requeueOrderToDispatchQueue(
                            order,
                            actorId,
                            role,
                            nonBlankOrDefault(request.notes(), "Đưa đơn về hàng chờ để lập tuyến giao lại ca sau"));
                }
                incident.setStatus("RESOLVED");
                incident.setResolvedBy(actorId);
                incident.setResolvedAt(Instant.now());
            }
            case "REASSIGN_DRIVER" -> {
                if (order != null) {
                    if (request.replacementDriverId() == null) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR);
                    }
                    if (order.getStatus() == OrderStatus.DELIVERED
                            || order.getStatus() == OrderStatus.CANCELLED
                            || order.getStatus() == OrderStatus.RETURNED) {
                        throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
                    }
                    UUID previousDriverId = order.getAssignedDriverId();
                    UUID replacementDriverId = request.replacementDriverId();
                    if (dispatchOrchestration != null
                            && previousDriverId != null
                            && !previousDriverId.equals(replacementDriverId)) {
                        dispatchOrchestration.synchronizePackageTransfer(
                                order, previousDriverId, replacementDriverId, "DELIVERY");
                    } else {
                        order.setAssignedDriverId(replacementDriverId);
                        execution.assign(order, replacementDriverId);
                    }
                    OrderStatus previousStatus = order.getStatus();
                    if (previousStatus == OrderStatus.FAILED
                            || previousStatus == OrderStatus.RETURNING
                            || previousStatus == OrderStatus.PLANNED) {
                        order.setStatus(OrderStatus.IN_TRANSIT);
                        execution.resumeDeliveryAfterIncident(order, replacementDriverId);
                        recordStatusChange(order, previousStatus, OrderStatus.IN_TRANSIT, actorId, role,
                                nonBlankOrDefault(request.notes(), "Điều phối phân công lại tài xế"));
                    }
                    order.setUpdatedAt(Instant.now());
                    orders.save(order);
                }
                incident.setStatus("RESOLVED");
                incident.setResolvedBy(actorId);
                incident.setResolvedAt(Instant.now());
            }
            case "REQUEST_RETURN" -> {
                if (order != null) {
                    if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
                        throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
                    }
                    OrderStatus previous = order.getStatus();
                    execution.markDeliveryTaskFailed(order, order.getAssignedDriverId());
                    order.setStatus(OrderStatus.RETURNING);
                    order.setUpdatedAt(Instant.now());
                    recordStatusChange(order, previous, OrderStatus.RETURNING, actorId, role,
                            nonBlankOrDefault(request.notes(), "Yêu cầu tài xế mang hàng hoàn về kho"));
                    orders.save(order);
                    execution.completeDeliveryPlanIfFinished(order);
                }
                incident.setStatus("RESOLVED");
                incident.setResolvedBy(actorId);
                incident.setResolvedAt(Instant.now());
            }
            default -> {
                incident.setStatus("RESOLVED");
                incident.setResolvedBy(actorId);
                incident.setResolvedAt(Instant.now());
            }
        }

        Map<String, Object> resolvedPayload = new LinkedHashMap<>();
        resolvedPayload.put("tenant_id", tenantId);
        resolvedPayload.put("incident_id", incident.getId());
        resolvedPayload.put("driver_id", incident.getDriverId() != null ? incident.getDriverId().toString() : "");
        resolvedPayload.put("order_id", order != null && order.getId() != null ? order.getId().toString() : "");
        resolvedPayload.put("order_code",
                order != null && order.getExternalOrderCode() != null ? order.getExternalOrderCode() : "");
        resolvedPayload.put("resolution_type", resType);
        resolvedPayload.put("notes", request.notes() != null ? request.notes() : "");
        resolvedPayload.put("status", incident.getStatus());
        resolvedPayload.put("resolved_by", actorId);
        emitOutbox("incident.resolved", tenantId, incident.getId(), resolvedPayload);

        return DeliveryIncidentResponse.from(incident);
    }

    // =========================================================================
    // 2. WAREHOUSE RETURN CHECK-IN & RE-DISPATCH (DIRECT ON ORDER)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ReturningOrderResponse> getReturningOrders(UUID tenantId, String statusFilter) {
        String normalized = statusFilter == null ? "ACTIVE" : statusFilter.trim().toUpperCase(Locale.ROOT);
        List<OrderStatus> statuses = switch (normalized) {
            case "ALL" -> List.of(OrderStatus.RETURNING, OrderStatus.FAILED, OrderStatus.RETURNED);
            case "RETURNING" -> List.of(OrderStatus.RETURNING);
            case "FAILED" -> List.of(OrderStatus.FAILED);
            case "RETURNED" -> List.of(OrderStatus.RETURNED);
            default -> List.of(OrderStatus.RETURNING, OrderStatus.FAILED);
        };

        List<Order> list = orders.findAllByTenantIdAndStatusInOrderByUpdatedAtDesc(tenantId, statuses);
        if (list.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> latestReasonByOrder = new HashMap<>();
        for (DeliveryIncident inc : incidents.findAllByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (inc.getOrder() != null) {
                latestReasonByOrder.putIfAbsent(
                        inc.getOrder().getId(),
                        joinReason(inc.getIncidentType(), inc.getNotes()));
            }
        }

        return list.stream()
                .map(order -> ReturningOrderResponse.from(
                        order,
                        latestReasonByOrder.getOrDefault(order.getId(), order.getNotes())))
                .toList();
    }

    @Transactional
    public ReturningOrderResponse confirmOrderReturn(
            UUID orderId,
            UUID tenantId,
            UUID actorId,
            String role,
            String notes) {
        requireOperator(role);
        Order order = owned(orderId, tenantId);

        if (order.getStatus() == OrderStatus.RETURNED) {
            return ReturningOrderResponse.from(order, notes);
        }
        if (order.getStatus() != OrderStatus.FAILED && order.getStatus() != OrderStatus.RETURNING) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        execution.markDeliveryTaskFailed(order, order.getAssignedDriverId());

        OrderStatus previous = order.getStatus();
        String cleanedNotes = notes == null || notes.isBlank() ? null : notes.trim();
        order.setStatus(OrderStatus.RETURNED);
        order.setUpdatedAt(Instant.now());
        recordStatusChange(
                order,
                previous,
                OrderStatus.RETURNED,
                actorId,
                role,
                nonBlankOrDefault(cleanedNotes, "Kho xác nhận đã nhận lại hàng hoàn"));
        orders.save(order);
        execution.completeDeliveryPlanIfFinished(order);

        resolveOpenOrderIncidents(
                order,
                tenantId,
                actorId,
                "RETURN_COMPLETED",
                nonBlankOrDefault(cleanedNotes, "Đã nhận hàng hoàn tại kho"));

        return ReturningOrderResponse.from(order, cleanedNotes);
    }

    @Transactional
    public ReturningOrderResponse requeueFailedOrder(
            UUID orderId,
            UUID tenantId,
            UUID actorId,
            String role,
            String notes) {
        requireOperator(role);
        Order order = owned(orderId, tenantId);

        if (order.getStatus() != OrderStatus.FAILED && order.getStatus() != OrderStatus.RETURNING) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        String cleanedNotes = notes == null || notes.isBlank() ? null : notes.trim();
        requeueOrderToDispatchQueue(
                order,
                actorId,
                role,
                nonBlankOrDefault(cleanedNotes, "Đưa đơn giao thất bại về lại hàng chờ điều phối"));

        resolveOpenOrderIncidents(
                order,
                tenantId,
                actorId,
                "RETRY_DELIVERY",
                nonBlankOrDefault(cleanedNotes, "Đã đưa đơn về hàng chờ để giao lại ca sau"));

        return ReturningOrderResponse.from(order, cleanedNotes);
    }

    private void requeueOrderToDispatchQueue(Order order, UUID actorId, String role, String reason) {
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        OrderStatus previous = order.getStatus();
        if (dispatchOrchestration != null) {
            dispatchOrchestration.detachOrderForRequeue(order);
        } else if (order.getPlanId() != null) {
            dispatchPlans.findById(order.getPlanId()).ifPresent(plan -> plan.removeOrder(order));
        }
        order.setAssignedDriverId(null);
        order.setStatus(OrderStatus.READY_FOR_DISPATCH);
        order.setUpdatedAt(Instant.now());
        recordStatusChange(order, previous, OrderStatus.READY_FOR_DISPATCH, actorId, role, reason);
        orders.save(order);
    }

    private void resolveOpenOrderIncidents(
            Order order,
            UUID tenantId,
            UUID actorId,
            String resolutionType,
            String resolutionNotes) {
        for (DeliveryIncident inc : incidents.findAllByOrder_IdOrderByCreatedAtDesc(order.getId())) {
            if (UNFINISHED_INCIDENT_STATUSES.contains(inc.getStatus())) {
                inc.setStatus("RESOLVED");
                inc.setResolutionType(resolutionType);
                inc.setResolvedBy(actorId);
                inc.setResolvedAt(Instant.now());
                inc.setResolutionNotes(resolutionNotes);
                incidents.save(inc);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("tenant_id", tenantId);
                payload.put("incident_id", inc.getId());
                payload.put("driver_id", inc.getDriverId() != null ? inc.getDriverId().toString() : "");
                payload.put("order_id", order.getId().toString());
                payload.put("order_code", order.getExternalOrderCode() != null ? order.getExternalOrderCode() : "");
                payload.put("resolution_type", resolutionType);
                payload.put("notes", resolutionNotes != null ? resolutionNotes : "");
                payload.put("status", "RESOLVED");
                payload.put("resolved_by", actorId);
                emitOutbox("incident.resolved", tenantId, inc.getId(), payload);
            }
        }
    }

    // =========================================================================
    // 3. COD COLLECTIONS & VERIFICATION
    // =========================================================================

    @Transactional
    public CodCollectionResponse collect(
            UUID orderId,
            UUID tenantId,
            UUID driverId,
            String role,
            CollectCodRequest request) {
        String outcome = request.amount().signum() == 0 ? "NOT_COLLECTED" : "FULL";
        return codLedger.recordCollection(orderId, tenantId, driverId, role,
                new RecordCodCollectionRequest(
                        UUID.randomUUID(), request.amount(), outcome, request.notes(), null, Instant.now()));
    }

    @Transactional
    public CodHandoverResponse declare(
            UUID id,
            UUID tenantId,
            UUID driverId,
            String role,
            CodHandoverRequest request) {
        return codLedger.submitHandover(tenantId, driverId, role,
                new SubmitCodHandoverRequest(UUID.randomUUID(), List.of(id), request.amount(), request.notes()));
    }

    @Transactional(readOnly = true)
    public List<DriverCodCollectionResponse> pendingHandover(UUID tenantId, UUID driverId, String role) {
        return codLedger.driverCustody(tenantId, driverId, role).eligibleCollections().stream()
                .map(collection -> new DriverCodCollectionResponse(
                        collection.id(),
                        collection.orderId(),
                        collection.orderCode(),
                        collection.amount(),
                        collection.reason(),
                        collection.occurredAt()))
                .toList();
    }

    @Transactional
    public CodHandoverResponse verify(UUID id, UUID tenantId, UUID actor, String role, String notes) {
        requireOperator(role);
        var batchOpt = codLedger.handoverQueue(tenantId, role, "SUBMITTED").stream()
                .filter(b -> b.id().equals(id))
                .findFirst();
        if (batchOpt.isPresent()) {
            return codLedger.verifyHandover(id, tenantId, actor, role,
                    new com.swiftroute.orderservice.dto.request.VerifyCodHandoverRequest(
                            UUID.randomUUID(), batchOpt.get().declaredTotal(), null, notes));
        }
        Order order = orders.findById(id).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getCodHandoverBatchId() != null) {
            return codLedger.verifyHandover(order.getCodHandoverBatchId(), tenantId, actor, role,
                    new com.swiftroute.orderservice.dto.request.VerifyCodHandoverRequest(
                            UUID.randomUUID(), order.getCodAmount(), null, notes));
        }
        order.setCodStatus("HANDED_OVER");
        order.setUpdatedAt(Instant.now());
        orders.save(order);
        return null;
    }

    @Transactional(readOnly = true)
    public OperationsReviewResponse review(UUID tenantId, String role) {
        requireOperator(role);

        var incList = incidents.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "OPEN").stream()
                .map(i -> new OperationsReviewResponse.IncidentItem(
                        i.getId(),
                        i.getOrder() != null ? i.getOrder().getId() : null,
                        i.getOrder() != null ? i.getOrder().getExternalOrderCode() : null,
                        i.getDispatchPlan() != null ? i.getDispatchPlan().getId() : null,
                        i.getDriverId(),
                        i.getIncidentType(),
                        i.getStatus(),
                        i.getNotes(),
                        i.getCreatedAt()))
                .toList();

        var retList = orders.findAllByTenantIdAndStatusInOrderByUpdatedAtDesc(
                tenantId, List.of(OrderStatus.RETURNING, OrderStatus.FAILED))
                .stream()
                .map(o -> new OperationsReviewResponse.ReturnItem(
                        o.getId(),
                        o.getId(),
                        o.getExternalOrderCode(),
                        o.getNotes(),
                        o.getStatus().name(),
                        o.getAssignedDriverId(),
                        o.getUpdatedAt()))
                .toList();

        var submittedBatches = codLedger.handoverQueue(tenantId, role, "SUBMITTED");
        List<OperationsReviewResponse.CodItem> codList = submittedBatches.stream()
                .map(batch -> new OperationsReviewResponse.CodItem(
                        batch.id(),
                        batch.driverId(),
                        batch.ledgerTotal(),
                        batch.declaredTotal(),
                        batch.lines().size(),
                        batch.lines().stream()
                                .map(line -> line.orderCode() != null ? line.orderCode() : line.orderId().toString())
                                .toList(),
                        batch.notes(),
                        batch.submittedAt()))
                .toList();

        return new OperationsReviewResponse(incList, retList, codList);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Order owned(UUID orderId, UUID tenantId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getTenant().getId().equals(tenantId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return order;
    }

    private void requireOperator(String role) {
        if (!isOperator(role)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private boolean isOperator(String role) {
        return OPERATOR_ROLES.contains(role == null ? "" : role.toUpperCase(Locale.ROOT));
    }

    private void recordStatusChange(
            Order order,
            OrderStatus from,
            OrderStatus to,
            UUID actorId,
            String role,
            String reason) {
        if (statusHistory != null) {
            statusHistory.record(order, from, to, actorId, role, reason);
        }
    }

    private static String joinReason(String type, String notes) {
        if (notes != null && !notes.isBlank()) {
            return type != null && !type.isBlank() ? type + ": " + notes.trim() : notes.trim();
        }
        return type;
    }

    private static String nonBlankOrDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private void emitOutbox(String eventType, UUID tenantId, UUID aggregateId, Map<String, Object> payload) {
        try {
            outboxEvents.save(OutboxEvent.builder()
                    .eventId(UUID.randomUUID())
                    .aggregateType("DELIVERY_INCIDENT")
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .build());
        } catch (Exception e) {
            if (eventType.startsWith("incident")) {
                throw new IllegalStateException("Unable to persist incident outbox event", e);
            }
            log.warn("Failed to serialize outbox event {}: {}", eventType, e.getMessage());
        }
    }
}
