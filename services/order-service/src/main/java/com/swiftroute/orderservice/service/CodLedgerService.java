package com.swiftroute.orderservice.service;

import com.swiftroute.orderservice.dto.response.PageRequests;
import com.swiftroute.orderservice.dto.response.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.RecordCodCollectionRequest;
import com.swiftroute.orderservice.dto.request.SubmitCodHandoverRequest;
import com.swiftroute.orderservice.dto.request.VerifyCodHandoverRequest;
import com.swiftroute.orderservice.dto.response.CodCollectionResponse;
import com.swiftroute.orderservice.dto.response.CodDashboardResponse;
import com.swiftroute.orderservice.dto.response.CodDisputeResponse;
import com.swiftroute.orderservice.dto.response.CodHandoverResponse;
import com.swiftroute.orderservice.dto.response.CodPaymentSummaryResponse;
import com.swiftroute.orderservice.dto.response.DriverCodCustodyResponse;
import com.swiftroute.orderservice.entity.CodHandoverBatch;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.CodHandoverBatchRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Streamlined, authoritative Cash-on-Delivery (COD) ledger service.
 * Tracks physical cash directly on the orders table and aggregates handovers
 * via cod_handover_batches. Redundant intermediate tables have been removed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodLedgerService {

    private static final Set<String> OPERATOR_ROLES = Set.of(
            "SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR", "CASHIER",
            "WAREHOUSE_STAFF", "TENANT_OWNER", "MANAGER", "STAFF");
    private static final Set<String> VARIANCE_RESOLUTIONS = Set.of(
            "DRIVER_RESPONSIBLE", "BUSINESS_ADJUSTMENT", "OTHER");

    private final OrderRepository orders;
    private final CodHandoverBatchRepository handovers;
    private final OutboxEventRepository outboxEvents;

    /**
     * Driver confirms cash collection upon successful physical delivery.
     */
    @Transactional
    public CodCollectionResponse recordCollection(
            UUID orderId,
            UUID tenantId,
            UUID driverId,
            String role,
            RecordCodCollectionRequest request) {
        requireDriver(role);

        Order order = ownedOrder(orderId, tenantId);

        if (order.getStatus() != OrderStatus.DELIVERED
                || order.getAssignedDriverId() == null
                || order.getCodAmount() == null
                || order.getCodAmount().signum() <= 0) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        if (!order.getAssignedDriverId().equals(driverId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        BigDecimal collected = money(request.amount());
        if (collected.compareTo(order.getCodAmount()) != 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        // Idempotent retry: if already collected, return current state
        if ("IN_CUSTODY".equals(order.getCodStatus()) || "HANDED_OVER".equals(order.getCodStatus())) {
            return collectionResponse(order);
        }

        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : Instant.now();
        order.setCodStatus("IN_CUSTODY");
        order.setCodCollectedAt(occurredAt);
        order.setPaymentStatus("PAID");
        order.setUpdatedAt(Instant.now());
        orders.save(order);

        emitCodEvent("cod.collected", order.getId(), Map.of(
                "tenant_id", tenantId.toString(),
                "driver_id", driverId.toString(),
                "order_id", order.getId().toString(),
                "cod_amount", order.getCodAmount(),
                "collected_at", occurredAt.toString()
        ));

        log.info("COD cash collected: order={}, driver={}, amount={}", order.getId(), driverId, order.getCodAmount());
        return collectionResponse(order);
    }

    /**
     * Get summary of COD payment status for an order.
     */
    @Transactional(readOnly = true)
    public CodPaymentSummaryResponse orderSummary(UUID orderId, UUID tenantId) {
        Order order = ownedOrder(orderId, tenantId);
        BigDecimal expected = order.getCodAmount() != null ? order.getCodAmount() : BigDecimal.ZERO;
        boolean collected = "IN_CUSTODY".equals(order.getCodStatus()) || "HANDED_OVER".equals(order.getCodStatus());
        BigDecimal total = collected ? expected : BigDecimal.ZERO;
        BigDecimal remaining = expected.subtract(total).max(BigDecimal.ZERO);

        return new CodPaymentSummaryResponse(
                order.getId(),
                order.getId(),
                order.getExternalOrderCode(),
                expected,
                total,
                remaining,
                order.getPaymentStatus(),
                "VND",
                List.of(collectionResponse(order)));
    }

    /**
     * Driver views their current physical cash custody and recent handovers.
     */
    @Transactional(readOnly = true)
    public DriverCodCustodyResponse driverCustody(UUID tenantId, UUID driverId, String role) {
        requireDriver(role);

        List<Order> inCustodyOrders = orders.findByTenant_IdAndAssignedDriverIdAndCodStatusOrderByUpdatedAtDesc(
                tenantId, driverId, "IN_CUSTODY");

        List<Order> handedOverOrders = orders.findByTenant_IdAndAssignedDriverIdAndCodStatusOrderByUpdatedAtDesc(
                tenantId, driverId, "HANDED_OVER");

        // Available: in custody and not yet submitted into any pending handover batch
        List<Order> eligible = inCustodyOrders.stream()
                .filter(o -> o.getCodHandoverBatchId() == null)
                .toList();

        // Locked: in custody but already submitted in a batch awaiting cashier verification
        List<Order> lockedOrders = inCustodyOrders.stream()
                .filter(o -> o.getCodHandoverBatchId() != null)
                .toList();

        BigDecimal available = eligible.stream()
                .map(Order::getCodAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal locked = lockedOrders.stream()
                .map(Order::getCodAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal handedOver = handedOverOrders.stream()
                .map(Order::getCodAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CodHandoverResponse> recent = handovers
                .findByTenantIdAndDriverIdOrderBySubmittedAtDesc(tenantId, driverId).stream()
                .limit(20)
                .map(this::handoverResponse)
                .toList();

        return new DriverCodCustodyResponse(
                driverId,
                available,
                locked,
                handedOver,
                BigDecimal.ZERO,
                eligible.stream().map(this::collectionResponse).toList(),
                recent);
    }

    /**
     * Driver submits an end-of-shift handover batch of collected cash.
     */
    @Transactional
    public CodHandoverResponse submitHandover(
            UUID tenantId,
            UUID driverId,
            String role,
            SubmitCodHandoverRequest request) {
        requireDriver(role);

        var repeated = handovers.findByTenantIdAndOperationId(tenantId, request.operationId());
        if (repeated.isPresent()) {
            if (!repeated.get().getDriverId().equals(driverId)) {
                throw new AppException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return handoverResponse(repeated.get());
        }

        List<UUID> orderIds = request.effectiveOrderIds();
        if (orderIds == null || orderIds.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        Set<UUID> uniqueIds = new HashSet<>(orderIds);
        if (uniqueIds.size() != orderIds.size()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        List<Order> selectedOrders = orders.findAllById(uniqueIds);
        if (selectedOrders.size() != uniqueIds.size()
                || selectedOrders.stream().anyMatch(o -> !o.getTenant().getId().equals(tenantId)
                        || !driverId.equals(o.getAssignedDriverId())
                        || !"IN_CUSTODY".equals(o.getCodStatus())
                        || o.getCodHandoverBatchId() != null
                        || o.getCodAmount() == null
                        || o.getCodAmount().signum() <= 0)) {
            throw new AppException(ErrorCode.INVALID_COD_HANDOVER);
        }

        BigDecimal ledgerTotal = selectedOrders.stream()
                .map(Order::getCodAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal declaredTotal = money(request.declaredTotal());
        if (ledgerTotal.compareTo(declaredTotal) != 0 && isBlank(request.notes())) {
            throw new AppException(ErrorCode.COD_REASON_REQUIRED);
        }

        CodHandoverBatch batch = handovers.save(CodHandoverBatch.builder()
                .tenantId(tenantId)
                .driverId(driverId)
                .operationId(request.operationId())
                .ledgerTotal(ledgerTotal)
                .declaredTotal(declaredTotal)
                .notes(request.notes())
                .status("SUBMITTED")
                .build());

        for (Order o : selectedOrders) {
            o.setCodHandoverBatchId(batch.getId());
            o.setUpdatedAt(Instant.now());
            orders.save(o);
        }

        emitCodEvent("cod.handover_submitted", batch.getId(), Map.of(
                "tenant_id", tenantId.toString(),
                "driver_id", driverId.toString(),
                "handover_id", batch.getId().toString(),
                "ledger_total", ledgerTotal,
                "declared_total", declaredTotal,
                "order_count", selectedOrders.size()
        ));

        log.info("COD handover submitted: batch={}, driver={}, amount={}, orders={}",
                batch.getId(), driverId, declaredTotal, selectedOrders.size());
        return handoverResponse(batch);
    }

    /**
     * Cashier / Operator verifies physical cash counted at the counter.
     */
    @Transactional
    public CodHandoverResponse verifyHandover(
            UUID handoverId,
            UUID tenantId,
            UUID cashierId,
            String role,
            VerifyCodHandoverRequest request) {
        requireOperator(role);

        var repeated = handovers.findByTenantIdAndVerificationOperationId(tenantId, request.operationId());
        if (repeated.isPresent()) {
            if (!repeated.get().getId().equals(handoverId)) {
                throw new AppException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return handoverResponse(repeated.get());
        }

        CodHandoverBatch batch = handovers.findByIdAndTenantId(handoverId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.COD_HANDOVER_NOT_FOUND));

        if (!"SUBMITTED".equals(batch.getStatus())) {
            throw new AppException(ErrorCode.INVALID_COD_HANDOVER);
        }

        BigDecimal counted = money(request.countedTotal());
        BigDecimal difference = counted.subtract(batch.getLedgerTotal());
        boolean matched = difference.signum() == 0;
        String varianceResolution = normalize(request.varianceResolution());
        if (!matched && (isBlank(request.notes()) || !VARIANCE_RESOLUTIONS.contains(varianceResolution))) {
            throw new AppException(ErrorCode.COD_REASON_REQUIRED);
        }

        Instant now = Instant.now();
        batch.setStatus(matched ? "SETTLED" : "SETTLED_WITH_VARIANCE");
        batch.setCountedTotal(counted);
        batch.setDiscrepancyAmount(difference);
        batch.setVarianceResolution(matched ? null : varianceResolution);
        batch.setVerifiedBy(cashierId);
        batch.setVerifiedAt(now);
        batch.setVerificationOperationId(request.operationId());
        batch.setUpdatedAt(now);
        if (!isBlank(request.notes())) {
            batch.setNotes(request.notes());
        }
        handovers.save(batch);

        // The cashier/manager made the final business decision in this request.
        // A variance is therefore settled explicitly instead of opening a vague
        // intermediate queue with no concrete next action.
        List<Order> batchOrders = orders.findByCodHandoverBatchIdOrderByUpdatedAtDesc(batch.getId());
        for (Order o : batchOrders) {
            o.setCodStatus("HANDED_OVER");
            o.setPaymentStatus("PAID");
            o.setUpdatedAt(now);
            orders.save(o);
        }

        emitCodEvent(matched ? "cod.handover_settled" : "cod.handover_settled_with_variance", batch.getId(), Map.of(
                "tenant_id", tenantId.toString(),
                "driver_id", batch.getDriverId().toString(),
                "handover_id", batch.getId().toString(),
                "ledger_total", batch.getLedgerTotal(),
                "counted_total", counted,
                "discrepancy_amount", difference,
                "status", batch.getStatus()
        ));

        log.info("COD handover {}: batch={}, cashier={}, counted={}, diff={}",
                batch.getStatus(), batch.getId(), cashierId, counted, difference);
        return handoverResponse(batch);
    }

    @Transactional(readOnly = true)
    public PageResponse<CodDisputeResponse> disputePage(UUID tenantId, String role, String status, int page, int size) {
        requireOperator(role);
        var pageable = PageRequests.newestFirst(page, size, "submittedAt");
        String targetStatus = isBlank(status) ? "DISCREPANCY" : normalize(status);
        return PageResponse.fromPage(handovers.findByTenantIdAndStatus(tenantId, targetStatus, pageable)
                .map(this::disputeResponse));
    }

    @Transactional(readOnly = true)
    public List<CodDisputeResponse> disputes(UUID tenantId, String role, String status) {
        requireOperator(role);
        String targetStatus = isBlank(status) ? "DISCREPANCY" : status.toUpperCase(Locale.ROOT);
        return handovers.findByTenantIdAndStatusOrderBySubmittedAtDesc(tenantId, targetStatus).stream()
                .map(this::disputeResponse)
                .toList();
    }

    @Transactional
    public CodHandoverResponse resolveDispute(
            UUID handoverId,
            UUID tenantId,
            UUID actorId,
            String role,
            String resolutionNotes) {
        requireOperator(role);
        if (isBlank(resolutionNotes)) {
            throw new AppException(ErrorCode.COD_REASON_REQUIRED);
        }

        CodHandoverBatch batch = handovers.findByIdAndTenantId(handoverId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.COD_HANDOVER_NOT_FOUND));

        if (!"DISCREPANCY".equals(batch.getStatus())) {
            throw new AppException(ErrorCode.INVALID_COD_DISPUTE);
        }

        Instant now = Instant.now();
        String combinedNotes = isBlank(batch.getNotes())
                ? resolutionNotes.trim()
                : batch.getNotes() + " | Resolved: " + resolutionNotes.trim();
        batch.setStatus("SETTLED_WITH_VARIANCE");
        batch.setVarianceResolution("OTHER");
        batch.setNotes(combinedNotes.length() > 1000 ? combinedNotes.substring(0, 1000) : combinedNotes);
        batch.setVerifiedBy(actorId);
        batch.setVerifiedAt(now);
        batch.setUpdatedAt(now);
        handovers.save(batch);

        List<Order> batchOrders = orders.findByCodHandoverBatchIdOrderByUpdatedAtDesc(batch.getId());
        for (Order o : batchOrders) {
            o.setCodStatus("HANDED_OVER");
            o.setPaymentStatus("PAID");
            o.setUpdatedAt(now);
            orders.save(o);
        }

        emitCodEvent("cod.dispute_resolved", batch.getId(), Map.of(
                "tenant_id", tenantId.toString(),
                "driver_id", batch.getDriverId().toString(),
                "handover_id", batch.getId().toString(),
                "discrepancy_amount", batch.getDiscrepancyAmount() != null ? batch.getDiscrepancyAmount() : BigDecimal.ZERO,
                "resolved_by", actorId.toString()
        ));

        log.info("COD discrepancy resolved: batch={}, actor={}", batch.getId(), actorId);
        return handoverResponse(batch);
    }

    @Transactional(readOnly = true)
    public PageResponse<CodHandoverResponse> handoverPage(UUID tenantId, String role, String status, int page, int size) {
        requireOperator(role);
        var pageable = PageRequests.newestFirst(page, size, "submittedAt");
        var rows = isBlank(status) ? handovers.findByTenantId(tenantId, pageable)
                : handovers.findByTenantIdAndStatus(tenantId, normalize(status), pageable);
        return PageResponse.fromPage(rows.map(this::handoverResponse));
    }

    @Transactional(readOnly = true)
    public List<CodHandoverResponse> handoverQueue(UUID tenantId, String role, String status) {
        requireOperator(role);
        List<CodHandoverBatch> rows = isBlank(status)
                ? handovers.findByTenantIdOrderBySubmittedAtDesc(tenantId)
                : handovers.findByTenantIdAndStatusOrderBySubmittedAtDesc(tenantId, status.toUpperCase(Locale.ROOT));
        return rows.stream().map(this::handoverResponse).toList();
    }

    @Transactional(readOnly = true)
    public CodDashboardResponse dashboard(UUID tenantId, String role) {
        requireOperator(role);

        List<Order> pendingOrders = orders.findByTenant_IdAndCodStatusOrderByUpdatedAtDesc(tenantId, "COD_PENDING");
        List<Order> custodyOrders = orders.findByTenant_IdAndCodStatusOrderByUpdatedAtDesc(tenantId, "IN_CUSTODY");
        List<CodHandoverBatch> submittedBatches = handovers.findByTenantIdAndStatusOrderBySubmittedAtDesc(tenantId, "SUBMITTED");
        List<CodHandoverBatch> discrepancyBatches = handovers.findByTenantIdAndStatusOrderBySubmittedAtDesc(tenantId, "DISCREPANCY");
        List<CodHandoverBatch> allBatches = handovers.findByTenantIdOrderBySubmittedAtDesc(tenantId);

        BigDecimal outstanding = pendingOrders.stream()
                .map(Order::getCodAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal inCustody = custodyOrders.stream()
                .map(Order::getCodAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal awaitingCashier = submittedBatches.stream()
                .map(CodHandoverBatch::getDeclaredTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openDiscrepancy = discrepancyBatches.stream()
                .map(b -> b.getDiscrepancyAmount() != null ? b.getDiscrepancyAmount().abs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CodHandoverResponse> recentHandovers = allBatches.stream()
                .limit(50)
                .map(this::handoverResponse)
                .toList();

        List<CodDisputeResponse> openDisputes = discrepancyBatches.stream()
                .map(this::disputeResponse)
                .toList();

        return new CodDashboardResponse(
                outstanding,
                inCustody,
                awaitingCashier,
                openDiscrepancy,
                pendingOrders.size(),
                custodyOrders.size(),
                submittedBatches.size(),
                discrepancyBatches.size(),
                recentHandovers,
                openDisputes);
    }

    private CodDisputeResponse disputeResponse(CodHandoverBatch batch) {
        BigDecimal difference = batch.getDiscrepancyAmount() != null ? batch.getDiscrepancyAmount() : BigDecimal.ZERO;
        String type = difference.signum() < 0 ? "SHORTAGE" : (difference.signum() > 0 ? "SURPLUS" : "NONE");
        return new CodDisputeResponse(
                batch.getId(),
                null,
                batch.getId(),
                null,
                null,
                batch.getDriverId(),
                type,
                batch.getLedgerTotal(),
                batch.getCountedTotal(),
                difference,
                batch.getStatus(),
                batch.getNotes(),
                null,
                isVarianceSettled(batch) ? batch.getVarianceResolution() : null,
                difference.abs(),
                isVarianceSettled(batch) ? batch.getNotes() : null,
                isVarianceSettled(batch) ? batch.getVerifiedBy() : null,
                isVarianceSettled(batch) ? batch.getVerifiedAt() : null,
                batch.getVerifiedAt() != null ? batch.getVerifiedAt() : batch.getSubmittedAt());
    }

    // =========================================================================
    // DTO MAPPERS & HELPERS
    // =========================================================================

    public CodCollectionResponse collectionResponse(Order order) {
        Instant collectedAt = order.getCodCollectedAt() != null ? order.getCodCollectedAt() : order.getUpdatedAt();
        return new CodCollectionResponse(
                order.getId(),
                order.getId(),
                order.getId(),
                order.getExternalOrderCode(),
                order.getAssignedDriverId(),
                order.getCodAmount() != null ? order.getCodAmount() : BigDecimal.ZERO,
                order.getCodAmount() != null ? order.getCodAmount() : BigDecimal.ZERO,
                "FULL",
                order.getCodStatus() != null ? order.getCodStatus() : "NOT_APPLICABLE",
                null,
                null,
                collectedAt,
                Instant.now());
    }

    public CodHandoverResponse handoverResponse(CodHandoverBatch handover) {
        List<Order> batchOrders = orders.findByCodHandoverBatchIdOrderByUpdatedAtDesc(handover.getId());
        List<CodHandoverResponse.Line> lines = batchOrders.stream()
                .map(o -> new CodHandoverResponse.Line(
                        o.getId(),
                        o.getId(),
                        o.getExternalOrderCode(),
                        o.getCodAmount() != null ? o.getCodAmount() : BigDecimal.ZERO,
                        "FULL"))
                .toList();

        return new CodHandoverResponse(
                handover.getId(),
                handover.getDriverId(),
                handover.getStatus(),
                handover.getLedgerTotal(),
                handover.getDeclaredTotal(),
                handover.getCountedTotal(),
                handover.getDiscrepancyAmount(),
                handover.getVarianceResolution(),
                handover.getNotes(),
                handover.getSubmittedAt(),
                handover.getVerifiedBy(),
                handover.getVerifiedAt(),
                lines);
    }

    private boolean isVarianceSettled(CodHandoverBatch batch) {
        return "SETTLED_WITH_VARIANCE".equals(batch.getStatus()) || "RESOLVED".equals(batch.getStatus());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private Order ownedOrder(UUID orderId, UUID tenantId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getTenant().getId().equals(tenantId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return order;
    }

    private void emitCodEvent(String eventType, UUID aggregateId, Map<String, Object> payload) {
        if (outboxEvents == null) return;
        outboxEvents.save(OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("COD_LEDGER")
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build());
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return value;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
