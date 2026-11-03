package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.CodHandoverRequest;
import com.swiftroute.orderservice.dto.request.CollectCodRequest;
import com.swiftroute.orderservice.dto.request.CreateReturnRequest;
import com.swiftroute.orderservice.dto.response.DriverCodCollectionResponse;
import com.swiftroute.orderservice.dto.response.OperationsReviewResponse;
import com.swiftroute.orderservice.entity.CodCollection;
import com.swiftroute.orderservice.entity.DeliveryIncident;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.CodCollectionRepository;
import com.swiftroute.orderservice.repository.DeliveryIncidentRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExceptionOperationsService {
    private static final Set<String> OPERATOR_ROLES = Set.of("ADMIN", "TENANT_ADMIN", "OPERATOR", "OPS_STAFF", "SYSTEM_ADMIN");
    private static final List<String> UNFINISHED_RETURN_STATUSES = List.of(
            "REQUESTED", "APPROVED", "IN_EXECUTION", "AWAITING_RECEIPT", "EXECUTION_FAILED");

    private final OrderRepository orders;
    private final DeliveryIncidentRepository incidents;
    private final ReturnRequestRepository returns;
    private final CodCollectionRepository cod;
    private final DeliveryExecutionService execution;
    private final PackageCustodyService custody;

    @Transactional
    public DeliveryIncident report(
            UUID orderId,
            UUID tenantId,
            UUID driverId,
            String role,
            String type,
            String notes) {
        requireDriver(role);
        Order order = owned(orderId, tenantId);
        if (!driverId.equals(order.getAssignedDriverId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return incidents.save(DeliveryIncident.builder()
                .order(order)
                .tenantId(tenantId)
                .reportedBy(driverId)
                .incidentType(type)
                .notes(notes)
                .build());
    }

    @Transactional
    public DeliveryIncident resolve(UUID id, UUID tenantId, UUID actor, String role, String notes) {
        requireOperator(role);
        DeliveryIncident incident = incidents.findById(id).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!incident.getTenantId().equals(tenantId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        if (returns.existsByOrder_IdAndStatusIn(incident.getOrder().getId(), UNFINISHED_RETURN_STATUSES)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        incident.setStatus("RESOLVED");
        incident.setResolvedBy(actor);
        incident.setResolutionNotes(notes);
        incident.setResolvedAt(Instant.now());
        return incident;
    }

    @Transactional
    public ReturnRequest requestReturn(
            UUID orderId,
            UUID tenantId,
            UUID actor,
            String role,
            CreateReturnRequest request) {
        requireOperator(role);
        Order order = owned(orderId, tenantId);
        if (order.getStatus() != OrderStatus.FAILED
                || returns.existsByOrder_IdAndStatusIn(orderId, UNFINISHED_RETURN_STATUSES)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        return returns.save(ReturnRequest.builder()
                .order(order)
                .tenantId(tenantId)
                .requestedBy(actor)
                .requestType(request.requestType())
                .reason(request.reason())
                .build());
    }

    @Transactional
    public ReturnRequest approveReturn(UUID id, UUID tenantId, UUID actor, String role) {
        requireOperator(role);
        ReturnRequest request = returns.findById(id).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!request.getTenantId().equals(tenantId) || !"REQUESTED".equals(request.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        Order order = request.getOrder();
        UUID driverId = order.getAssignedDriverId();
        if (driverId == null) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        request.setApprovedBy(actor);
        request.setUpdatedAt(Instant.now());
        if ("REDELIVERY".equals(request.getRequestType())) {
            execution.reactivateForRedelivery(order, driverId);
            request.setStatus("COMPLETED");
            request.setCompletionNotes("Đã tạo lượt giao lại");
            request.setReceivedBy(actor);
            request.setReceivedAt(Instant.now());
            return request;
        }

        custody.startReturn(order, actor);
        DeliveryTask returnTask = execution.createReturnTask(order, driverId);
        request.setReturnTask(returnTask);
        request.setStatus("IN_EXECUTION");
        order.setFulfillmentStatus("RETURNING");
        order.setUpdatedAt(Instant.now());
        return request;
    }

    @Transactional
    public ReturnRequest confirmReturnReceipt(
            UUID id,
            UUID tenantId,
            UUID actor,
            String role,
            String notes) {
        requireOperator(role);
        ReturnRequest request = returns.findById(id).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!request.getTenantId().equals(tenantId)
                || !"AWAITING_RECEIPT".equals(request.getStatus())
                || request.getReturnTask() == null
                || request.getReturnTask().getStatus() != DeliveryTaskStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        Order order = request.getOrder();
        custody.receiveReturn(order, actor, notes);
        request.setStatus("COMPLETED");
        request.setReceivedBy(actor);
        request.setReceivedAt(Instant.now());
        request.setCompletionNotes(notes);
        request.setUpdatedAt(Instant.now());
        order.setStatus(OrderStatus.RETURNED);
        order.setFulfillmentStatus("RETURNED");
        order.setUpdatedAt(Instant.now());
        return request;
    }

    @Transactional
    public CodCollection collect(
            UUID orderId,
            UUID tenantId,
            UUID driverId,
            String role,
            CollectCodRequest request) {
        requireDriver(role);
        Order order = owned(orderId, tenantId);
        if (order.getStatus() != OrderStatus.DELIVERED || !driverId.equals(order.getAssignedDriverId())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        if (order.getCodAmount() == null
                || order.getCodAmount().signum() <= 0
                || request.amount().compareTo(order.getCodAmount()) != 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (cod.findByOrder_Id(orderId).isPresent()) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        return cod.save(CodCollection.builder()
                .order(order)
                .tenantId(tenantId)
                .driverId(driverId)
                .collectedAmount(request.amount())
                .notes(request.notes())
                .build());
    }

    @Transactional
    public CodCollection declare(
            UUID id,
            UUID tenantId,
            UUID driverId,
            String role,
            CodHandoverRequest request) {
        requireDriver(role);
        CodCollection collection = cod.findById(id).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!collection.getTenantId().equals(tenantId)
                || !collection.getDriverId().equals(driverId)
                || !"PENDING_HANDOVER".equals(collection.getStatus())
                || collection.getCollectedAmount().compareTo(request.amount()) != 0) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        collection.setDeclaredAmount(request.amount());
        collection.setDeclaredAt(Instant.now());
        collection.setNotes(request.notes());
        collection.setStatus("DECLARED");
        return collection;
    }

    @Transactional(readOnly = true)
    public List<DriverCodCollectionResponse> pendingHandover(UUID tenantId, UUID driverId, String role) {
        requireDriver(role);
        return cod.findByTenantIdAndDriverIdAndStatusOrderByCollectedAtDesc(tenantId, driverId, "PENDING_HANDOVER")
                .stream()
                .map(collection -> new DriverCodCollectionResponse(
                        collection.getId(),
                        collection.getOrder().getId(),
                        collection.getOrder().getExternalOrderCode(),
                        collection.getCollectedAmount(),
                        collection.getNotes(),
                        collection.getCollectedAt()))
                .toList();
    }

    @Transactional
    public CodCollection verify(UUID id, UUID tenantId, UUID actor, String role, String notes) {
        requireOperator(role);
        CodCollection collection = cod.findById(id).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!collection.getTenantId().equals(tenantId) || !"DECLARED".equals(collection.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        collection.setStatus(collection.getCollectedAmount().compareTo(collection.getDeclaredAmount()) == 0
                ? "VERIFIED"
                : "DISCREPANCY");
        collection.setVerifiedBy(actor);
        collection.setVerifiedAt(Instant.now());
        collection.setResolutionNotes(notes);
        return collection;
    }

    @Transactional(readOnly = true)
    public OperationsReviewResponse review(UUID tenantId, String role) {
        requireOperator(role);
        return new OperationsReviewResponse(
                incidents.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "OPEN").stream()
                        .map(incident -> new OperationsReviewResponse.IncidentItem(
                                incident.getId(), incident.getOrder().getId(), incident.getOrder().getExternalOrderCode(),
                                incident.getIncidentType(), incident.getNotes(), incident.getCreatedAt()))
                        .toList(),
                returns.findByTenantIdAndStatusInOrderByCreatedAtDesc(
                                tenantId, List.of("REQUESTED", "AWAITING_RECEIPT", "EXECUTION_FAILED"))
                        .stream()
                        .map(request -> new OperationsReviewResponse.ReturnItem(
                                request.getId(), request.getOrder().getId(), request.getOrder().getExternalOrderCode(),
                                request.getRequestType(), request.getReason(), request.getStatus(),
                                request.getReturnTask() == null ? request.getOrder().getAssignedDriverId() : request.getReturnTask().getDriverId(),
                                request.getCreatedAt()))
                        .toList(),
                cod.findByTenantIdAndStatusOrderByCollectedAtDesc(tenantId, "DECLARED").stream()
                        .map(collection -> new OperationsReviewResponse.CodItem(
                                collection.getId(), collection.getOrder().getId(), collection.getOrder().getExternalOrderCode(),
                                collection.getDriverId(), collection.getCollectedAmount(), collection.getDeclaredAmount(),
                                collection.getNotes(), collection.getDeclaredAt()))
                        .toList());
    }

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
        if (!OPERATOR_ROLES.contains(role == null ? "" : role.toUpperCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
