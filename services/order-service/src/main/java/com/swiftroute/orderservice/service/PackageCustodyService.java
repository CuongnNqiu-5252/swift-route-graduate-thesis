package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.AssignPackageHandoverRequest;
import com.swiftroute.orderservice.dto.request.CreatePackageHandoverRequest;
import com.swiftroute.orderservice.dto.response.PackageHandoverResponse;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.PackageCustodyEvent;
import com.swiftroute.orderservice.entity.PackageHandover;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;
import com.swiftroute.orderservice.repository.PackageCustodyEventRepository;
import com.swiftroute.orderservice.repository.PackageHandoverRepository;
import com.swiftroute.orderservice.service.planning.DispatchOrchestrationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PackageCustodyService {
    private static final List<String> ACTIVE_HANDOVER_STATUSES = List.of("REQUESTED", "ASSIGNED");
    private static final Set<String> OPERATOR_ROLES = Set.of(
            "SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR", "WAREHOUSE_STAFF",
            "TENANT_OWNER", "MANAGER", "STAFF");

    private final PackageCustodyEventRepository custodyEvents;
    private final PackageHandoverRepository handovers;
    private final OrderRepository orders;
    private final ReturnRequestRepository returns;
    private final OperationalReadinessService readiness;
    private final ObjectProvider<DispatchOrchestrationService> dispatchOrchestration;

    @Transactional
    public void acquireAtPickup(Order order, UUID driverId) {
        requireAssignedDriver(order, driverId);
        requireNoActiveHandover(order);
        if (!holdsOrder(order)) {
            record(order, "PICKUP_CONFIRMED", null, driverId, null, driverId, "Tài xế xác nhận lấy hàng");
        }
    }

    @Transactional
    public void releaseAtDelivery(Order order, UUID driverId) {
        requireHeldOrder(order, driverId);
        requireNoActiveHandover(order);
        record(order, "DELIVERY_RELEASED", driverId, null, null, driverId, "Giao hàng thành công");
    }

    @Transactional
    public void startReturn(Order order, UUID actorId) {
        UUID driverId = order.getAssignedDriverId();
        requireAssignedDriver(order, driverId);
        requireNoActiveHandover(order);
        record(order, "RETURN_STARTED", driverId, driverId, null, actorId, "Yêu cầu hoàn hàng được duyệt");
    }

    @Transactional
    public void receiveReturn(Order order, UUID actorId, String notes) {
        UUID driverId = order.getAssignedDriverId();
        requireReturnCustody(order, driverId);
        record(order, "RETURN_RECEIVED", driverId, null, null, actorId, notes);
    }

    public void requireReturnCustody(Order order, UUID driverId) {
        requireHeldOrder(order, driverId);
        requireNoActiveHandover(order);
        if (!"RETURNING".equals(order.getFulfillmentStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    public boolean holdsOrder(Order order) {
        return custodyEvents.findFirstByOrder_IdOrderByCreatedAtDesc(order.getId())
                .map(event -> event.getToCustodianId() != null)
                .orElse(false);
    }

    public void requireNoActiveHandover(Order order) {
        if (handovers.existsByOrder_IdAndStatusIn(order.getId(), ACTIVE_HANDOVER_STATUSES)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    private void requireAssignedDriver(Order order, UUID driverId) {
        if (driverId == null || !driverId.equals(order.getAssignedDriverId())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    private void requireHeldOrder(Order order, UUID driverId) {
        requireAssignedDriver(order, driverId);
        if (!custodyEvents.findFirstByOrder_IdOrderByCreatedAtDesc(order.getId())
                .map(event -> driverId.equals(event.getToCustodianId())).orElse(false)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    @Transactional
    public PackageHandoverResponse createHandover(
            UUID tenantId,
            UUID driverId,
            String role,
            CreatePackageHandoverRequest request) {
        requireDriver(role);
        Order order = orders.findForUpdate(request.orderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!tenantId.equals(order.getTenant().getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        requireHeldOrder(order, driverId);
        requireNoActiveHandover(order);

        order.setUpdatedAt(Instant.now());
        PackageHandover handover = PackageHandover.builder()
                .order(order)
                .tenantId(tenantId)
                .fromDriverId(driverId)
                .reason(request.reason().trim())
                .build();
        return PackageHandoverResponse.from(handovers.saveAndFlush(handover));
    }

    @Transactional
    public PackageHandoverResponse assign(
            UUID handoverId,
            UUID tenantId,
            UUID operatorId,
            String role,
            AssignPackageHandoverRequest request) {
        requireOperator(role);
        PackageHandover handover = ownedHandover(handoverId, tenantId);
        if (!"REQUESTED".equals(handover.getStatus())
                || handover.getFromDriverId().equals(request.toDriverId())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        readiness.requireAssignment(handover.getOrder().getTenant(), request.toDriverId());

        Instant now = Instant.now();
        handover.setToDriverId(request.toDriverId());
        handover.setStatus("ASSIGNED");
        handover.setAssignedBy(operatorId);
        handover.setAssignedAt(now);
        handover.setAdminNotes(trimToNull(request.notes()));
        handover.setUpdatedAt(now);
        return PackageHandoverResponse.from(handover);
    }

    @Transactional
    public PackageHandoverResponse receiverConfirm(UUID handoverId, UUID tenantId, UUID driverId, String role, String notes) {
        requireDriver(role);
        PackageHandover handover = ownedHandover(handoverId, tenantId);
        if (!driverId.equals(handover.getToDriverId()) || !"ASSIGNED".equals(handover.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        Instant now = Instant.now();
        Order order = orders.findForUpdate(handover.getOrder().getId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        requireHeldOrder(order, handover.getFromDriverId());
        String taskType = "RETURNING".equals(order.getFulfillmentStatus()) ? "RETURN" : "DELIVERY";
        dispatchOrchestration.getObject().synchronizePackageTransfer(order, handover.getFromDriverId(), driverId, taskType);
        returns.findAllByOrder_IdAndCurrentHolderIdAndStatusIn(order.getId(), handover.getFromDriverId(),
                List.of("REQUESTED", "ASSIGNED", "IN_EXECUTION", "ARRIVED", "AWAITING_RECEIPT",
                        "DISCREPANCY", "EXECUTION_FAILED"))
                .forEach(request -> {
                    request.setCurrentHolderId(driverId);
                    request.setUpdatedAt(now);
                });
        record(order, "HANDOVER_COMPLETED", handover.getFromDriverId(), driverId, handover.getId(), driverId, notes);
        handover.setStatus("COMPLETED");
        handover.setCompletedAt(now);
        handover.setUpdatedAt(now);
        handover.setReceiverNotes(trimToNull(notes));

        return PackageHandoverResponse.from(handover);
    }

    @Transactional
    public PackageHandoverResponse reject(
            UUID handoverId,
            UUID tenantId,
            UUID operatorId,
            String role,
            String notes) {
        requireOperator(role);
        PackageHandover handover = ownedHandover(handoverId, tenantId);
        if (!"REQUESTED".equals(handover.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        Instant now = Instant.now();
        handover.setStatus("REJECTED");
        handover.setRejectedBy(operatorId);
        handover.setRejectedAt(now);
        handover.setAdminNotes(requireNotes(notes));
        handover.setUpdatedAt(now);
        return PackageHandoverResponse.from(handover);
    }

    @Transactional
    public PackageHandoverResponse cancel(
            UUID handoverId,
            UUID tenantId,
            UUID actorId,
            String role,
            String notes) {
        PackageHandover handover = ownedHandover(handoverId, tenantId);
        boolean operator = isOperator(role);
        boolean requestingDriver = "DRIVER".equalsIgnoreCase(role)
                && actorId.equals(handover.getFromDriverId())
                && "REQUESTED".equals(handover.getStatus());
        if ((!operator && !requestingDriver)
                || !Set.of("REQUESTED", "ASSIGNED").contains(handover.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        Instant now = Instant.now();
        handover.setStatus("CANCELLED");
        handover.setCancelledBy(actorId);
        handover.setCancelledAt(now);
        handover.setAdminNotes(requireNotes(notes));
        handover.setUpdatedAt(now);
        return PackageHandoverResponse.from(handover);
    }

    @Transactional(readOnly = true)
    public List<PackageHandoverResponse> myHandovers(UUID tenantId, UUID driverId, String role) {
        requireDriver(role);
        return handovers.findDriverHandovers(tenantId, driverId, ACTIVE_HANDOVER_STATUSES).stream()
                .map(PackageHandoverResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PackageHandoverResponse> operationalQueue(UUID tenantId, String role) {
        requireOperator(role);
        return handovers.findOperationalQueue(tenantId, ACTIVE_HANDOVER_STATUSES).stream()
                .map(PackageHandoverResponse::from)
                .toList();
    }

    private PackageHandover ownedHandover(UUID id, UUID tenantId) {
        PackageHandover handover = handovers.findById(id).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!handover.getTenantId().equals(tenantId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return handover;
    }

    private void record(
            Order order,
            String type,
            UUID from,
            UUID to,
            UUID handoverId,
            UUID actor,
            String reason) {
        custodyEvents.save(PackageCustodyEvent.builder()
                .order(order)
                .tenantId(order.getTenant().getId())
                .eventType(type)
                .fromCustodianId(from)
                .toCustodianId(to)
                .handoverId(handoverId)
                .actorId(actor)
                .reason(reason)
                .build());
    }

    private boolean isOperator(String role) {
        return OPERATOR_ROLES.contains(role == null ? "" : role.toUpperCase(Locale.ROOT));
    }

    private void requireOperator(String role) {
        if (!isOperator(role)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void requireDriver(String role) {
        if (!"DRIVER".equalsIgnoreCase(role)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private String requireNotes(String notes) {
        String value = trimToNull(notes);
        if (value == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
