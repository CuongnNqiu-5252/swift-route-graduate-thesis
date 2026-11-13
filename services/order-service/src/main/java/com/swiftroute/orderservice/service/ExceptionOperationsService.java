package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.orderservice.dto.request.ApproveReturnRequest;
import com.swiftroute.orderservice.dto.request.CodHandoverRequest;
import com.swiftroute.orderservice.dto.request.CollectCodRequest;
import com.swiftroute.orderservice.dto.request.CreateIncidentRequest;
import com.swiftroute.orderservice.dto.request.CreateReturnRequest;
import com.swiftroute.orderservice.dto.request.ReportReturnDiscrepancyRequest;
import com.swiftroute.orderservice.dto.request.RecordCodCollectionRequest;
import com.swiftroute.orderservice.dto.request.ResolveIncidentRequest;
import com.swiftroute.orderservice.dto.request.SubmitCodHandoverRequest;
import com.swiftroute.orderservice.dto.response.CodCollectionResponse;
import com.swiftroute.orderservice.dto.response.CodHandoverResponse;
import com.swiftroute.orderservice.dto.response.DeliveryIncidentResponse;
import com.swiftroute.orderservice.dto.response.DriverCodCollectionResponse;
import com.swiftroute.orderservice.dto.response.OperationsReviewResponse;
import com.swiftroute.orderservice.dto.response.ReturnRequestResponse;
import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.DeliveryIncident;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.PickupLocation;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.DeliveryIncidentRepository;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.PickupLocationRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;
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

    private static final List<String> ACTIVE_RETURN_STATUSES = List.of(
            "REQUESTED", "APPROVED", "ASSIGNED", "IN_EXECUTION", "ARRIVED", "AWAITING_RECEIPT");

    private static final List<String> UNFINISHED_INCIDENT_STATUSES = List.of(
            "OPEN", "IN_PROGRESS", "ESCALATED");

    private final OrderRepository orders;
    private final DeliveryIncidentRepository incidents;
    private final ReturnRequestRepository returns;
    private final DeliveryExecutionService execution;
    private final PackageCustodyService custody;
    private final RecoveryRuleEngine recoveryRuleEngine;
    private final DispatchPlanRepository dispatchPlans;
    private final PickupLocationRepository pickupLocations;
    private final DeliveryAttemptRepository deliveryAttempts;
    private final OutboxEventRepository outboxEvents;
    private final ObjectMapper objectMapper;
    private final CodLedgerService codLedger;

    // =========================================================================
    // 1. DELIVERY INCIDENTS MANAGEMENT
    // =========================================================================

    /**
     * Reports an operational delivery incident.
     * Can be order-level (e.g. customer unreachable, damaged goods) or route-level (vehicle breakdown).
     */
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

        // Operational failures reuse an existing open incident. An offline-sync conflict is
        // an audit record for a distinct field claim, so it must not be swallowed by an
        // unrelated incident already open on the order.
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

        boolean pureDelayOnRoute = Set.of("TRAFFIC_CONGESTION", "WEATHER_DELAY").contains(normalizedType)
                && Boolean.TRUE.equals(request.canContinueDelivery());

        // Determine delivery impact: any order-level delivery blocker (unreachable, wrong address, refused, damaged, etc.)
        // marks the current delivery task and attempt for this order as FAILED, even if the driver can continue delivering other orders.
        if (order != null && !pureDelayOnRoute && !auditOnlyIncident) {
            // Record failed attempt and mark active delivery task as FAILED
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
                    .failureReason(request.incidentType())
                    .notes(request.notes())
                    .proofUrl(photoProof)
                    .photoUrl(photoProof)
                    .contactAttempts(request.contactAttempts() != null ? request.contactAttempts() : 1)
                    .completedBy(reporterId)
                    .build();
            deliveryAttempts.save(attempt);

            order.setStatus(OrderStatus.FAILED);
            order.setFulfillmentStatus("FAILED");
            order.setUpdatedAt(Instant.now());
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
                .incidentType(request.incidentType().toUpperCase(Locale.ROOT))
                .severity(request.severity() != null ? request.severity().toUpperCase(Locale.ROOT) : "MEDIUM")
                .notes(request.notes())
                .evidenceUrls(evidenceUrlsStr)
                .canContinueDelivery(Boolean.TRUE.equals(request.canContinueDelivery()))
                .needsImmediateAssistance(Boolean.TRUE.equals(request.needsImmediateAssistance()))
                .status("OPEN")
                .build();

        DeliveryIncident saved = incidents.save(incident);
        recoveryRuleEngine.evaluateAndApply(saved);

        emitOutbox("incident.reported", tenantId, saved.getId(), Map.of(
                "incident_id", saved.getId(),
                "order_id", order != null ? order.getId() : "",
                "incident_type", saved.getIncidentType(),
                "severity", saved.getSeverity(),
                "reported_by", reporterId
        ));

        log.info("Delivery incident reported: id={}, type={}, severity={}, order={}, plan={}",
                saved.getId(), saved.getIncidentType(), saved.getSeverity(),
                order != null ? order.getId() : "NONE", plan != null ? plan.getId() : "NONE");

        return DeliveryIncidentResponse.from(saved);
    }

    /**
     * Query delivery incidents with optional filters.
     */
    @Transactional(readOnly = true)
    public List<DeliveryIncidentResponse> getIncidents(
            UUID tenantId,
            String status,
            String severity,
            UUID driverId,
            UUID orderId) {

        List<DeliveryIncident> list = incidents.findAllByTenantIdOrderByCreatedAtDesc(tenantId);

        return list.stream()
                .filter(i -> status == null || status.isBlank() || i.getStatus().equalsIgnoreCase(status))
                .filter(i -> severity == null || severity.isBlank() || i.getSeverity().equalsIgnoreCase(severity))
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

    /**
     * Resolves an operational delivery incident using one of the authorized resolutions.
     */
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

        Order order = incident.getOrder();

        switch (resType) {
            case "CONTINUE_DELIVERY" -> {
                if (order != null) {
                    execution.resumeDeliveryAfterIncident(order, order.getAssignedDriverId());
                    order.setStatus(OrderStatus.IN_TRANSIT);
                    order.setFulfillmentStatus("IN_TRANSIT");
                    order.setUpdatedAt(Instant.now());
                }
                incident.setStatus("RESOLVED");
                incident.setResolvedBy(actorId);
                incident.setResolvedAt(Instant.now());
            }
            case "RETRY_DELIVERY" -> {
                if (order != null) {
                    execution.reactivateForRedelivery(order, order.getAssignedDriverId());
                    order.setStatus(OrderStatus.PLANNED);
                    order.setFulfillmentStatus("PLANNED");
                    order.setUpdatedAt(Instant.now());
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
                    // Physical custody safety check: Never reassign without verified custody
                    var units = custody.packagesForOrder(order.getId(), tenantId, actorId, role);
                    boolean hasCustody = units.stream().anyMatch(u -> "IN_CUSTODY".equals(u.status()));
                    if (hasCustody) {
                        throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
                    }
                    order.setAssignedDriverId(request.replacementDriverId());
                    order.setUpdatedAt(Instant.now());
                }
                incident.setStatus("RESOLVED");
                incident.setResolvedBy(actorId);
                incident.setResolvedAt(Instant.now());
            }
            case "REQUEST_RETURN" -> {
                if (order != null) {
                    execution.markDeliveryTaskFailed(order, order.getAssignedDriverId());
                    order.setStatus(OrderStatus.FAILED);
                    order.setFulfillmentStatus("FAILED");
                    order.setUpdatedAt(Instant.now());
                    if (returns.existsByOrder_IdAndStatusIn(order.getId(), ACTIVE_RETURN_STATUSES)) {
                        throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
                    }
                    createReturnRequest(tenantId, actorId, role, new CreateReturnRequest(
                            order.getId(),
                            incident.getId(),
                            "RETURN_TO_ORIGIN",
                            request.notes() != null ? request.notes() : "Sự cố giao hàng yêu cầu hoàn trả",
                            null,
                            order.getPackageCount(),
                            request.notes(),
                            null
                    ));
                }
                // Incident remains IN_PROGRESS until physical return completes
                incident.setStatus("IN_PROGRESS");
            }
            case "ESCALATED" -> {
                incident.setStatus("ESCALATED");
                incident.setAssignedOperatorId(request.assignedOperatorId() != null
                        ? request.assignedOperatorId()
                        : actorId);
            }
            default -> {
                incident.setStatus("RESOLVED");
                incident.setResolvedBy(actorId);
                incident.setResolvedAt(Instant.now());
            }
        }

        Map<String, Object> resolvedPayload = new java.util.LinkedHashMap<>();
        resolvedPayload.put("tenant_id", tenantId);
        resolvedPayload.put("incident_id", incident.getId());
        resolvedPayload.put("driver_id", incident.getDriverId() != null ? incident.getDriverId().toString() : "");
        resolvedPayload.put("order_id", order != null && order.getId() != null ? order.getId().toString() : "");
        resolvedPayload.put("order_code", order != null && order.getExternalOrderCode() != null ? order.getExternalOrderCode() : "");
        resolvedPayload.put("resolution_type", resType);
        resolvedPayload.put("notes", request.notes() != null ? request.notes() : "");
        resolvedPayload.put("status", incident.getStatus());
        resolvedPayload.put("resolved_by", actorId);
        emitOutbox("incident.resolved", tenantId, incident.getId(), resolvedPayload);

        return DeliveryIncidentResponse.from(incident);
    }

    // =========================================================================
    // 2. RETURN-TO-SENDER (RTS) MANAGEMENT
    // =========================================================================

    /**
     * Creates a new Return Request (status = REQUESTED).
     */
    @Transactional
    public ReturnRequestResponse createReturnRequest(
            UUID tenantId,
            UUID actorId,
            String role,
            CreateReturnRequest request) {

        requireOperator(role);

        Order order = owned(request.orderId(), tenantId);

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (returns.existsByOrder_IdAndStatusIn(order.getId(), ACTIVE_RETURN_STATUSES)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        // Determine destination return location
        PickupLocation returnLocation = null;
        if (request.returnLocationId() != null) {
            returnLocation = pickupLocations.findByIdAndTenantId(request.returnLocationId(), tenantId)
                    .orElseThrow(() -> new AppException(ErrorCode.PICKUP_LOCATION_NOT_FOUND));
        } else if (order.getPickupLocation() != null) {
            returnLocation = order.getPickupLocation();
        } else {
            returnLocation = pickupLocations.findByTenantIdAndIsDefaultTrueAndStatus(tenantId, com.swiftroute.orderservice.entity.enums.PickupLocationStatus.ACTIVE)
                    .orElse(null);
        }

        DeliveryIncident incident = null;
        if (request.incidentId() != null) {
            incident = incidents.findByIdAndTenantId(request.incidentId(), tenantId).orElse(null);
        }

        UUID holderId = order.getAssignedDriverId();

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .incident(incident)
                .tenantId(tenantId)
                .returnLocation(returnLocation)
                .currentHolderId(holderId)
                .packageCount(request.packageCount() != null ? request.packageCount() : order.getPackageCount())
                .requestType(request.requestType() != null ? request.requestType() : "RETURN_TO_ORIGIN")
                .reason(request.reason())
                .status("REQUESTED")
                .requestedBy(actorId)
                .discrepancyEvidenceUrl(request.evidenceUrl())
                .build();

        ReturnRequest saved = returns.save(returnRequest);

        emitOutbox("return.requested", tenantId, saved.getId(), Map.of(
                "return_request_id", saved.getId(),
                "order_id", order.getId(),
                "current_holder_id", holderId != null ? holderId : "",
                "status", "REQUESTED"
        ));

        log.info("Return request created: id={}, orderId={}, holderId={}, reason={}",
                saved.getId(), order.getId(), holderId, saved.getReason());

        return ReturnRequestResponse.from(saved);
    }

    /**
     * Query return requests with optional filters.
     */
    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> getReturnRequests(
            UUID tenantId,
            String status,
            UUID driverId) {

        List<ReturnRequest> list = returns.findAllByTenantIdOrderByCreatedAtDesc(tenantId);

        return list.stream()
                .filter(r -> status == null || status.isBlank() || r.getStatus().equalsIgnoreCase(status))
                .filter(r -> driverId == null || driverId.equals(r.getCurrentHolderId()))
                .map(ReturnRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse getReturnRequestById(UUID id, UUID tenantId) {
        ReturnRequest req = returns.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return ReturnRequestResponse.from(req);
    }

    /**
     * Approves a Return Request and schedules the return task.
     */
    @Transactional
    public ReturnRequestResponse approveReturn(
            UUID id,
            UUID tenantId,
            UUID actorId,
            String role,
            ApproveReturnRequest request) {

        requireOperator(role);

        ReturnRequest returnReq = returns.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!"REQUESTED".equals(returnReq.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (request != null && request.returnLocationId() != null) {
            PickupLocation loc = pickupLocations.findByIdAndTenantId(request.returnLocationId(), tenantId)
                    .orElseThrow(() -> new AppException(ErrorCode.PICKUP_LOCATION_NOT_FOUND));
            returnReq.setReturnLocation(loc);
        }

        Order order = returnReq.getOrder();
        UUID driverId = (request != null && request.assignedDriverId() != null)
                ? request.assignedDriverId()
                : (returnReq.getCurrentHolderId() != null ? returnReq.getCurrentHolderId() : order.getAssignedDriverId());

        if (driverId == null) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        returnReq.setApprovedBy(actorId);
        returnReq.setUpdatedAt(Instant.now());

        if ("REDELIVERY".equalsIgnoreCase(returnReq.getRequestType())) {
            execution.reactivateForRedelivery(order, driverId);
            returnReq.setStatus("COMPLETED");
            returnReq.setCompletionNotes("Đã lên lịch giao lại");
            returnReq.setReceivedBy(actorId);
            returnReq.setReceivedAt(Instant.now());
            return ReturnRequestResponse.from(returnReq);
        }

        // Normal return custody execution
        execution.markDeliveryTaskFailed(order, driverId);
        custody.startReturn(order, actorId);
        DeliveryTask returnTask = execution.createReturnTask(order, driverId);
        returnReq.setReturnTask(returnTask);
        returnReq.setCurrentHolderId(driverId);
        returnReq.setStatus("IN_EXECUTION");

        order.setStatus(OrderStatus.FAILED);
        order.setFulfillmentStatus("RETURNING");
        order.setUpdatedAt(Instant.now());

        emitOutbox("return.approved", tenantId, returnReq.getId(), Map.of(
                "tenant_id", tenantId,
                "return_request_id", returnReq.getId(),
                "order_id", order.getId(),
                "order_code", order.getExternalOrderCode() != null ? order.getExternalOrderCode() : "",
                "driver_id", driverId.toString(),
                "assigned_driver_id", driverId,
                "return_location_name", returnReq.getReturnLocation() != null && returnReq.getReturnLocation().getName() != null
                        ? returnReq.getReturnLocation().getName() : "kho lấy hàng",
                "status", "IN_EXECUTION"
        ));

        log.info("Return request approved: id={}, assignedDriver={}, taskId={}",
                returnReq.getId(), driverId, returnTask.getId());

        return ReturnRequestResponse.from(returnReq);
    }

    /**
     * Driver confirms arrival at return destination warehouse.
     */
    @Transactional
    public ReturnRequestResponse arriveReturnLocation(
            UUID id,
            UUID tenantId,
            UUID driverId,
            String role) {

        requireDriver(role);

        ReturnRequest returnReq = returns.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!driverId.equals(returnReq.getCurrentHolderId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (!Set.of("ASSIGNED", "IN_EXECUTION").contains(returnReq.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        returnReq.setStatus("ARRIVED");
        returnReq.setUpdatedAt(Instant.now());

        if (returnReq.getReturnTask() != null) {
            returnReq.getReturnTask().setStatus(DeliveryTaskStatus.ARRIVED);
        }

        return ReturnRequestResponse.from(returnReq);
    }

    /**
     * Driver initiates physical package handover to warehouse staff.
     */
    @Transactional
    public ReturnRequestResponse initiateReturnHandover(
            UUID id,
            UUID tenantId,
            UUID driverId,
            String role) {

        requireDriver(role);

        ReturnRequest returnReq = returns.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!driverId.equals(returnReq.getCurrentHolderId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (!Set.of("ARRIVED", "IN_EXECUTION").contains(returnReq.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        returnReq.setStatus("AWAITING_RECEIPT");
        returnReq.setUpdatedAt(Instant.now());

        log.info("Return handover initiated by driver {}: returnId={}", driverId, returnReq.getId());
        return ReturnRequestResponse.from(returnReq);
    }

    /**
     * Warehouse receiving staff confirms physical receipt of returned goods.
     * DRIVERS CANNOT CALL THIS METHOD.
     */
    @Transactional
    public ReturnRequestResponse confirmReturnReceipt(
            UUID id,
            UUID tenantId,
            UUID receiverId,
            String role,
            String notes) {

        requireOperator(role);

        ReturnRequest returnReq = returns.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!Set.of("IN_EXECUTION", "ASSIGNED", "ARRIVED", "AWAITING_RECEIPT").contains(returnReq.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        Order order = returnReq.getOrder();

        // Authoritative custody release: driver is no longer responsible
        custody.receiveReturn(order, receiverId, notes);

        returnReq.setStatus("COMPLETED");
        returnReq.setReceivedBy(receiverId);
        returnReq.setReceivedAt(Instant.now());
        returnReq.setCompletionNotes(notes);
        returnReq.setCurrentHolderId(null);
        returnReq.setUpdatedAt(Instant.now());

        if (returnReq.getReturnTask() != null) {
            returnReq.getReturnTask().setStatus(DeliveryTaskStatus.COMPLETED);
        }

        order.setStatus(OrderStatus.RETURNED);
        order.setFulfillmentStatus("RETURNED");
        order.setUpdatedAt(Instant.now());

        // Resolve linked incident if open
        if (returnReq.getIncident() != null) {
            DeliveryIncident inc = returnReq.getIncident();
            inc.setStatus("RESOLVED");
            inc.setResolutionType("RETURN_COMPLETED");
            inc.setResolvedBy(receiverId);
            inc.setResolvedAt(Instant.now());
            inc.setResolutionNotes("Đã nhận hàng hoàn tại kho: " + (notes != null ? notes : ""));
            emitOutbox("incident.resolved", tenantId, inc.getId(), Map.of(
                    "tenant_id", tenantId,
                    "incident_id", inc.getId(),
                    "resolution_type", "RETURN_COMPLETED",
                    "status", "RESOLVED",
                    "resolved_by", receiverId
            ));
        }

        emitOutbox("return.completed", tenantId, returnReq.getId(), Map.of(
                "return_request_id", returnReq.getId(),
                "order_id", order.getId(),
                "received_by", receiverId,
                "status", "COMPLETED"
        ));

        log.info("Return receipt confirmed by {}: returnId={}, orderId={}", receiverId, returnReq.getId(), order.getId());

        return ReturnRequestResponse.from(returnReq);
    }

    /**
     * Warehouse receiving staff reports discrepancy (damaged, missing goods).
     */
    @Transactional
    public ReturnRequestResponse reportReturnDiscrepancy(
            UUID id,
            UUID tenantId,
            UUID receiverId,
            String role,
            ReportReturnDiscrepancyRequest request) {

        requireOperator(role);

        ReturnRequest returnReq = returns.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!Set.of("IN_EXECUTION", "ASSIGNED", "ARRIVED", "AWAITING_RECEIPT").contains(returnReq.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        returnReq.setStatus("DISCREPANCY");
        returnReq.setDiscrepancyType(request.discrepancyType().toUpperCase(Locale.ROOT));
        returnReq.setActualQuantity(request.actualQuantity());
        returnReq.setDiscrepancyNotes(request.discrepancyNotes());
        returnReq.setDiscrepancyEvidenceUrl(request.evidenceUrl());
        returnReq.setUpdatedAt(Instant.now());

        // Auto-create or link investigation incident
        DeliveryIncident discIncident = DeliveryIncident.builder()
                .order(returnReq.getOrder())
                .tenantId(tenantId)
                .reportedBy(receiverId)
                .driverId(returnReq.getCurrentHolderId())
                .incidentType("RETURN_DISCREPANCY_" + returnReq.getDiscrepancyType())
                .severity("HIGH")
                .notes("Chênh lệch khi nhận hàng hoàn: " + (request.discrepancyNotes() != null ? request.discrepancyNotes() : ""))
                .evidenceUrls(request.evidenceUrl())
                .status("OPEN")
                .build();
        DeliveryIncident savedInc = incidents.save(discIncident);

        if (returnReq.getIncident() == null) {
            returnReq.setIncident(savedInc);
        }

        emitOutbox("return.discrepancy", tenantId, returnReq.getId(), Map.of(
                "return_request_id", returnReq.getId(),
                "discrepancy_type", returnReq.getDiscrepancyType(),
                "reported_by", receiverId
        ));

        log.warn("Return discrepancy reported: returnId={}, type={}, notes={}",
                returnReq.getId(), returnReq.getDiscrepancyType(), returnReq.getDiscrepancyNotes());

        return ReturnRequestResponse.from(returnReq);
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
                            UUID.randomUUID(), batchOpt.get().declaredTotal(), notes));
        }
        Order order = orders.findById(id).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getCodHandoverBatchId() != null) {
            return codLedger.verifyHandover(order.getCodHandoverBatchId(), tenantId, actor, role,
                    new com.swiftroute.orderservice.dto.request.VerifyCodHandoverRequest(
                            UUID.randomUUID(), order.getCodAmount(), notes));
        }
        order.setCodStatus("HANDED_OVER");
        order.setPaymentStatus("PAID");
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
                        i.getSeverity(),
                        i.getStatus(),
                        i.getNotes(),
                        i.getCanContinueDelivery() != null && i.getCanContinueDelivery(),
                        i.getNeedsImmediateAssistance() != null && i.getNeedsImmediateAssistance(),
                        i.getCreatedAt()))
                .toList();

        var retList = returns.findByTenantIdAndStatusInOrderByCreatedAtDesc(
                        tenantId, List.of("REQUESTED", "AWAITING_RECEIPT", "EXECUTION_FAILED", "DISCREPANCY"))
                .stream()
                .map(r -> new OperationsReviewResponse.ReturnItem(
                        r.getId(),
                        r.getOrder() != null ? r.getOrder().getId() : null,
                        r.getOrder() != null ? r.getOrder().getExternalOrderCode() : null,
                        r.getRequestType(),
                        r.getReason(),
                        r.getStatus(),
                        r.getCurrentHolderId() != null
                                ? r.getCurrentHolderId()
                                : (r.getOrder() != null ? r.getOrder().getAssignedDriverId() : null),
                        r.getCreatedAt()))
                .toList();

        var submittedBatches = codLedger.handoverQueue(tenantId, role, "SUBMITTED");
        List<OperationsReviewResponse.CodItem> codList = new java.util.ArrayList<>();
        for (var batch : submittedBatches) {
            for (var line : batch.lines()) {
                codList.add(new OperationsReviewResponse.CodItem(
                        batch.id(),
                        line.orderId(),
                        line.orderCode(),
                        batch.driverId(),
                        line.amount(),
                        batch.declaredTotal(),
                        batch.notes(),
                        batch.submittedAt()));
            }
        }

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

    private void requireDriver(String role) {
        if (!"DRIVER".equalsIgnoreCase(role)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void requireOperator(String role) {
        if (!isOperator(role)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private boolean isOperator(String role) {
        return OPERATOR_ROLES.contains(role == null ? "" : role.toUpperCase(Locale.ROOT));
    }

    private void emitOutbox(String eventType, UUID tenantId, UUID aggregateId, Map<String, Object> payload) {
        try {
            outboxEvents.save(OutboxEvent.builder()
                    .eventId(UUID.randomUUID())
                    .aggregateType(eventType.startsWith("incident") ? "DELIVERY_INCIDENT" : "RETURN_REQUEST")
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
