package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.request.CreatePackageHandoverRequest;
import com.swiftroute.orderservice.dto.response.PackageHandoverResponse;
import com.swiftroute.orderservice.dto.response.PackageUnitResponse;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.PackageCustodyEvent;
import com.swiftroute.orderservice.entity.PackageHandover;
import com.swiftroute.orderservice.entity.PackageHandoverItem;
import com.swiftroute.orderservice.entity.PackageUnit;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.PackageCustodyEventRepository;
import com.swiftroute.orderservice.repository.PackageHandoverRepository;
import com.swiftroute.orderservice.repository.PackageUnitRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PackageCustodyService {
    private static final List<String> ACTIVE_HANDOVER_STATUSES = List.of("REQUESTED", "SENDER_CONFIRMED", "DISPUTED");

    private final PackageUnitRepository packageUnits;
    private final PackageCustodyEventRepository custodyEvents;
    private final PackageHandoverRepository handovers;
    private final DeliveryTaskRepository deliveryTasks;
    private final OrderRepository orders;

    @Transactional
    public void acquireAtPickup(Order order, UUID driverId) {
        List<PackageUnit> units = packageUnits.findByOrder_IdOrderByPackageNumberAsc(order.getId());
        if (units.isEmpty()) {
            int count = order.getPackageCount() == null ? 1 : order.getPackageCount();
            for (int index = 1; index <= count; index++) {
                units.add(packageUnits.save(PackageUnit.builder()
                        .order(order)
                        .tenantId(order.getTenant().getId())
                        .packageNumber(index)
                        .packageCode(order.getTrackingToken() + "-P" + String.format("%02d", index))
                        .build()));
            }
        }

        for (PackageUnit unit : units) {
            if (unit.getCurrentCustodianId() != null || !"PENDING_PICKUP".equals(unit.getStatus())) {
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
            }
            unit.setCurrentCustodianId(driverId);
            unit.setStatus("IN_CUSTODY");
            unit.setUpdatedAt(Instant.now());
            record(unit, "PICKUP_CONFIRMED", null, driverId, null, driverId, "Tài xế xác nhận lấy hàng");
        }
    }

    @Transactional
    public void releaseAtDelivery(Order order, UUID driverId) {
        List<PackageUnit> units = requireOrderCustody(order, driverId);
        for (PackageUnit unit : units) {
            unit.setCurrentCustodianId(null);
            unit.setStatus("DELIVERED");
            unit.setUpdatedAt(Instant.now());
            record(unit, "DELIVERY_RELEASED", driverId, null, null, driverId, "Giao hàng thành công");
        }
    }

    @Transactional
    public void startReturn(Order order, UUID actorId) {
        List<PackageUnit> units = packageUnits.findByOrder_IdOrderByPackageNumberAsc(order.getId());
        if (units.isEmpty() || units.stream().anyMatch(unit -> unit.getCurrentCustodianId() == null)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        for (PackageUnit unit : units) {
            if (!"IN_CUSTODY".equals(unit.getStatus())) {
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
            }
            unit.setStatus("RETURNING");
            unit.setUpdatedAt(Instant.now());
            record(unit, "RETURN_STARTED", unit.getCurrentCustodianId(), unit.getCurrentCustodianId(), null, actorId,
                    "Yêu cầu hoàn về nơi gửi đã được phê duyệt");
        }
    }

    @Transactional
    public void receiveReturn(Order order, UUID actorId, String notes) {
        List<PackageUnit> units = packageUnits.findByOrder_IdOrderByPackageNumberAsc(order.getId());
        if (units.isEmpty() || units.stream().anyMatch(unit -> unit.getCurrentCustodianId() == null || !"RETURNING".equals(unit.getStatus()))) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        for (PackageUnit unit : units) {
            UUID previous = unit.getCurrentCustodianId();
            unit.setCurrentCustodianId(null);
            unit.setStatus("RETURNED");
            unit.setUpdatedAt(Instant.now());
            record(unit, "RETURN_RECEIVED", previous, null, null, actorId, notes);
        }
    }

    @Transactional(readOnly = true)
    public void requireReturnCustody(Order order, UUID driverId) {
        List<PackageUnit> units = packageUnits.findByOrder_IdOrderByPackageNumberAsc(order.getId());
        if (units.isEmpty() || units.stream().anyMatch(unit ->
                !driverId.equals(unit.getCurrentCustodianId()) || !"RETURNING".equals(unit.getStatus()))) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    @Transactional(readOnly = true)
    public List<PackageUnitResponse> packagesForOrder(UUID orderId, UUID tenantId, UUID actorId, String role) {
        Order order = orders.findById(orderId).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        boolean operator = isOperator(role);
        boolean assignedDriver = order.getAssignedDriverId() != null && order.getAssignedDriverId().equals(actorId);
        if (!order.getTenant().getId().equals(tenantId) || (!operator && !assignedDriver)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return packageUnits.findByOrder_IdOrderByPackageNumberAsc(orderId).stream().map(PackageUnitResponse::from).toList();
    }

    @Transactional
    public PackageHandoverResponse createHandover(
            UUID tenantId,
            UUID driverId,
            String role,
            CreatePackageHandoverRequest request) {
        requireDriver(role);
        if (driverId.equals(request.toDriverId())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(request.packageIds()));
        if (ids.size() != request.packageIds().size()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        List<PackageUnit> units = packageUnits.findByIdIn(ids);
        if (units.size() != ids.size() || units.stream().anyMatch(unit ->
                !unit.getTenantId().equals(tenantId)
                        || !driverId.equals(unit.getCurrentCustodianId())
                        || !Set.of("IN_CUSTODY", "RETURNING").contains(unit.getStatus()))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        PackageHandover handover = PackageHandover.builder()
                .tenantId(tenantId)
                .fromDriverId(driverId)
                .toDriverId(request.toDriverId())
                .reason(request.reason().trim())
                .requestedBy(driverId)
                .build();
        for (PackageUnit unit : units) {
            handover.getItems().add(PackageHandoverItem.builder()
                    .handover(handover)
                    .packageUnit(unit)
                    .previousStatus(unit.getStatus())
                    .build());
            unit.setStatus("HANDOVER_PENDING");
            unit.setUpdatedAt(Instant.now());
        }
        return PackageHandoverResponse.from(handovers.saveAndFlush(handover));
    }

    @Transactional
    public PackageHandoverResponse senderConfirm(UUID handoverId, UUID tenantId, UUID driverId, String role, String notes) {
        requireDriver(role);
        PackageHandover handover = ownedHandover(handoverId, tenantId);
        if (!driverId.equals(handover.getFromDriverId()) || !"REQUESTED".equals(handover.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        handover.setStatus("SENDER_CONFIRMED");
        handover.setSenderConfirmedAt(Instant.now());
        handover.setUpdatedAt(Instant.now());
        handover.setNotes(notes);
        return PackageHandoverResponse.from(handover);
    }

    @Transactional
    public PackageHandoverResponse receiverConfirm(UUID handoverId, UUID tenantId, UUID driverId, String role, String notes) {
        requireDriver(role);
        PackageHandover handover = ownedHandover(handoverId, tenantId);
        if (!driverId.equals(handover.getToDriverId()) || !"SENDER_CONFIRMED".equals(handover.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        Instant now = Instant.now();
        Set<UUID> affectedOrders = new LinkedHashSet<>();
        for (PackageHandoverItem item : handover.getItems()) {
            PackageUnit unit = item.getPackageUnit();
            if (!"PENDING".equals(item.getStatus())
                    || !"HANDOVER_PENDING".equals(unit.getStatus())
                    || !handover.getFromDriverId().equals(unit.getCurrentCustodianId())) {
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
            }
            unit.setCurrentCustodianId(driverId);
            unit.setStatus("RETURNING".equals(item.getPreviousStatus()) ? "RETURNING" : "IN_CUSTODY");
            unit.setUpdatedAt(now);
            item.setStatus("TRANSFERRED");
            affectedOrders.add(unit.getOrder().getId());
            record(unit, "HANDOVER_COMPLETED", handover.getFromDriverId(), driverId, handover.getId(), driverId, notes);
        }
        handover.setStatus("COMPLETED");
        handover.setReceiverConfirmedAt(now);
        handover.setCompletedAt(now);
        handover.setUpdatedAt(now);
        handover.setNotes(notes);

        for (UUID orderId : affectedOrders) {
            List<PackageUnit> orderUnits = packageUnits.findByOrder_IdOrderByPackageNumberAsc(orderId);
            if (!orderUnits.isEmpty() && orderUnits.stream().allMatch(unit -> driverId.equals(unit.getCurrentCustodianId()))) {
                Order order = orderUnits.get(0).getOrder();
                order.setAssignedDriverId(driverId);
                order.setUpdatedAt(now);
                deliveryTasks.findByOrder_IdAndTaskType(orderId, "DELIVERY")
                        .filter(task -> task.getStatus() != DeliveryTaskStatus.COMPLETED && task.getStatus() != DeliveryTaskStatus.CANCELLED)
                        .ifPresent(task -> task.setDriverId(driverId));
                deliveryTasks.findByOrder_IdAndTaskType(orderId, "RETURN")
                        .filter(task -> task.getStatus() != DeliveryTaskStatus.COMPLETED && task.getStatus() != DeliveryTaskStatus.CANCELLED)
                        .ifPresent(task -> task.setDriverId(driverId));
            }
        }
        return PackageHandoverResponse.from(handover);
    }

    @Transactional
    public PackageHandoverResponse cancel(UUID handoverId, UUID tenantId, UUID driverId, String role, String notes) {
        requireDriver(role);
        PackageHandover handover = ownedHandover(handoverId, tenantId);
        if (!driverId.equals(handover.getFromDriverId()) || !Set.of("REQUESTED", "SENDER_CONFIRMED").contains(handover.getStatus())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        for (PackageHandoverItem item : handover.getItems()) {
            if (!"PENDING".equals(item.getStatus())) {
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
            }
            item.setStatus("CANCELLED");
            item.getPackageUnit().setStatus(item.getPreviousStatus());
            item.getPackageUnit().setUpdatedAt(Instant.now());
        }
        handover.setStatus("CANCELLED");
        handover.setCancelledAt(Instant.now());
        handover.setUpdatedAt(Instant.now());
        handover.setNotes(notes);
        return PackageHandoverResponse.from(handover);
    }

    @Transactional(readOnly = true)
    public List<PackageHandoverResponse> myHandovers(UUID tenantId, UUID driverId, String role) {
        requireDriver(role);
        return handovers.findDriverHandovers(tenantId, driverId, ACTIVE_HANDOVER_STATUSES).stream()
                .map(PackageHandoverResponse::from)
                .toList();
    }

    private List<PackageUnit> requireOrderCustody(Order order, UUID driverId) {
        List<PackageUnit> units = packageUnits.findByOrder_IdOrderByPackageNumberAsc(order.getId());
        if (units.isEmpty() || units.stream().anyMatch(unit ->
                !driverId.equals(unit.getCurrentCustodianId()) || !"IN_CUSTODY".equals(unit.getStatus()))) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        return units;
    }

    private PackageHandover ownedHandover(UUID id, UUID tenantId) {
        PackageHandover handover = handovers.findById(id).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!handover.getTenantId().equals(tenantId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return handover;
    }

    private void record(
            PackageUnit unit,
            String type,
            UUID from,
            UUID to,
            UUID handoverId,
            UUID actor,
            String reason) {
        custodyEvents.save(PackageCustodyEvent.builder()
                .packageUnit(unit)
                .tenantId(unit.getTenantId())
                .eventType(type)
                .fromCustodianId(from)
                .toCustodianId(to)
                .handoverId(handoverId)
                .actorId(actor)
                .reason(reason)
                .build());
    }

    private boolean isOperator(String role) {
        return Set.of("ADMIN", "TENANT_ADMIN", "OPERATOR", "OPS_STAFF", "SYSTEM_ADMIN")
                .contains(role == null ? "" : role.toUpperCase(Locale.ROOT));
    }

    private void requireDriver(String role) {
        if (!"DRIVER".equalsIgnoreCase(role)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
