package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.event.DeliveryCompletedEvent;
import com.swiftroute.orderservice.dto.request.BatchDeliveryTaskActionRequest;
import com.swiftroute.orderservice.dto.request.UpdateDeliveryTaskRequest;
import com.swiftroute.orderservice.dto.response.DriverDeliveryTaskResponse;
import com.swiftroute.orderservice.entity.AuditLog;
import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.AuditLogRepository;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;
import com.swiftroute.orderservice.repository.ReturnRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeliveryExecutionService {
    private static final EnumSet<DeliveryTaskStatus> ACTIVE = EnumSet.of(
            DeliveryTaskStatus.ASSIGNED,
            DeliveryTaskStatus.ACCEPTED,
            DeliveryTaskStatus.ARRIVED,
            DeliveryTaskStatus.PICKED_UP,
            DeliveryTaskStatus.OUT_FOR_DELIVERY);

    private final DeliveryTaskRepository tasks;
    private final DeliveryAttemptRepository attempts;
    private final OrderRepository orders;
    private final AuditLogRepository auditLogs;
    private final ReturnRequestRepository returnRequests;
    private final PackageCustodyService custody;
    private final OutboxEventRepository outboxEvents;

    @Transactional
    public DeliveryTask assign(Order order, UUID driverId) {
        return tasks.findByOrder_IdAndTaskType(order.getId(), "DELIVERY")
                .map(task -> {
                    if (!task.getDriverId().equals(driverId)) {
                        if (task.getStatus().ordinal() >= DeliveryTaskStatus.PICKED_UP.ordinal()
                                && task.getStatus() != DeliveryTaskStatus.FAILED) {
                            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
                        }
                        task.setDriverId(driverId);
                    }
                    return task;
                })
                .orElseGet(() -> tasks.save(DeliveryTask.builder()
                        .order(order)
                        .tenantId(order.getTenant().getId())
                        .driverId(driverId)
                        .build()));
    }

    @Transactional
    public DeliveryTask markDeliveryTaskFailed(Order order, UUID driverId) {
        UUID effectiveDriver = driverId != null ? driverId : order.getAssignedDriverId();
        DeliveryTask task = tasks.findByOrder_IdAndTaskType(order.getId(), "DELIVERY")
                .orElseGet(() -> DeliveryTask.builder()
                        .order(order)
                        .tenantId(order.getTenant().getId())
                        .driverId(effectiveDriver)
                        .taskType("DELIVERY")
                        .build());
        task.setStatus(DeliveryTaskStatus.FAILED);
        task.setUpdatedAt(Instant.now());
        return tasks.save(task);
    }

    @Transactional
    public DeliveryTask createReturnTask(Order order, UUID driverId) {
        return tasks.findByOrder_IdAndTaskType(order.getId(), "RETURN")
                .map(task -> {
                    if (task.getStatus() != DeliveryTaskStatus.CANCELLED && task.getStatus() != DeliveryTaskStatus.FAILED) {
                        throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
                    }
                    task.setDriverId(driverId);
                    task.setStatus(DeliveryTaskStatus.ASSIGNED);
                    task.setUpdatedAt(Instant.now());
                    return task;
                })
                .orElseGet(() -> tasks.save(DeliveryTask.builder()
                        .order(order)
                        .tenantId(order.getTenant().getId())
                        .driverId(driverId)
                        .taskType("RETURN")
                        .build()));
    }

    @Transactional
    public DeliveryTask reactivateForRedelivery(Order order, UUID driverId) {
        DeliveryTask task = tasks.findByOrder_IdAndTaskType(order.getId(), "DELIVERY")
                .orElseGet(() -> DeliveryTask.builder()
                        .order(order)
                        .tenantId(order.getTenant().getId())
                        .driverId(driverId)
                        .taskType("DELIVERY")
                        .status(DeliveryTaskStatus.FAILED)
                        .build());
        com.swiftroute.orderservice.security.TenantRequestInterceptor.assertRequestTenant(task.getTenantId());
        if (task.getStatus() == DeliveryTaskStatus.COMPLETED || task.getStatus() == DeliveryTaskStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        task.setDriverId(driverId);
        task.setStatus(DeliveryTaskStatus.ASSIGNED);
        task.setUpdatedAt(Instant.now());
        order.setAssignedDriverId(driverId);
        order.setStatus(OrderStatus.PLANNED);
        order.setFulfillmentStatus("PLANNED");
        order.setUpdatedAt(Instant.now());
        return tasks.save(task);
    }

    @Transactional(readOnly = true)
    public List<DriverDeliveryTaskResponse> activeForDriver(UUID driverId, String role) {
        requireDriver(role);
        return tasks.findByDriverIdAndStatusIn(driverId, ACTIVE).stream()
                .filter(task -> task.getOrder().getStatus() != OrderStatus.CANCELLED)
                .map(DriverDeliveryTaskResponse::from)
                .toList();
    }

    @Transactional
    public DeliveryTask updateForOrder(UUID orderId, UUID driverId, String role, UpdateDeliveryTaskRequest request) {
        DeliveryTask task = tasks.findByOrder_IdAndTaskType(orderId, "DELIVERY")
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        com.swiftroute.orderservice.security.TenantRequestInterceptor.assertRequestTenant(task.getTenantId());
        return update(task.getId(), driverId, role, request);
    }

    @Transactional
    public DeliveryTask update(UUID taskId, UUID driverId, String role, UpdateDeliveryTaskRequest request) {
        requireDriver(role);
        DeliveryTask task = tasks.findById(taskId).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        com.swiftroute.orderservice.security.TenantRequestInterceptor.assertRequestTenant(task.getTenantId());
        if (!task.getDriverId().equals(driverId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        DeliveryTaskStatus previous = task.getStatus();
        // The order remains authoritative even if a stale task is still active.
        if (task.getOrder().getStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (!allowed(task.getTaskType(), previous, request.status())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        if (request.status() == DeliveryTaskStatus.FAILED
                && (request.failureReason() == null || request.failureReason().isBlank())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        Order order = task.getOrder();
        if ("DELIVERY".equals(task.getTaskType())
                && previous.ordinal() < DeliveryTaskStatus.PICKED_UP.ordinal()
                && (request.status() == DeliveryTaskStatus.PICKED_UP
                        || request.status() == DeliveryTaskStatus.OUT_FOR_DELIVERY)) {
            custody.acquireAtPickup(order, driverId);
        }
        if ("DELIVERY".equals(task.getTaskType()) && request.status() == DeliveryTaskStatus.COMPLETED) {
            custody.releaseAtDelivery(order, driverId);
        }
        if ("RETURN".equals(task.getTaskType()) && request.status() == DeliveryTaskStatus.COMPLETED) {
            custody.requireReturnCustody(order, driverId);
        }

        task.setStatus(request.status());
        task.setUpdatedAt(Instant.now());
        applyOrderState(task, order, driverId, request);

        if ("DELIVERY".equals(task.getTaskType()) && request.status() == DeliveryTaskStatus.COMPLETED) {
            publishDeliveryCompleted(task, order);
        }

        if (request.status() == DeliveryTaskStatus.COMPLETED || request.status() == DeliveryTaskStatus.FAILED) {
            attempts.save(DeliveryAttempt.builder()
                    .order(order)
                    .task(task)
                    .tenantId(task.getTenantId())
                    .attemptNumber(attempts.maxAttemptNumber(order.getId()) + 1)
                    .outcome(request.status() == DeliveryTaskStatus.COMPLETED ? "SUCCESS" : "FAILED")
                    .failureReason(request.failureReason())
                    .notes(request.notes())
                    .proofUrl(request.proofUrl())
                    .completedBy(driverId)
                    .build());
        }

        orders.save(order);
        audit(task, previous, request, driverId);
        return task;
    }

    private void publishDeliveryCompleted(DeliveryTask task, Order order) {
        UUID eventId = UUID.randomUUID();
        Instant completedAt = Instant.now();
        DeliveryCompletedEvent payload = new DeliveryCompletedEvent(
                eventId,
                order.getId(),
                task.getId(),
                task.getTenantId(),
                task.getDriverId(),
                order.getPickupLat(),
                order.getPickupLng(),
                order.getDeliveryLat(),
                order.getDeliveryLng(),
                order.getWeightKg(),
                order.getDeliveryFee(),
                completedAt);
        outboxEvents.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("DELIVERY_TASK")
                .aggregateId(task.getId())
                .eventType("order.delivery_completed.v1")
                .payload(payload)
                .build());
    }

    private void applyOrderState(
            DeliveryTask task,
            Order order,
            UUID driverId,
            UpdateDeliveryTaskRequest request) {
        if ("RETURN".equals(task.getTaskType())) {
            if (request.status() == DeliveryTaskStatus.OUT_FOR_DELIVERY) {
                order.setFulfillmentStatus("RETURNING");
            }
            if (request.status() == DeliveryTaskStatus.COMPLETED) {
                ReturnRequest returnRequest = returnRequests.findByReturnTask_Id(task.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.INVALID_ORDER_STATUS));
                returnRequest.setStatus("AWAITING_RECEIPT");
                returnRequest.setUpdatedAt(Instant.now());
                order.setFulfillmentStatus("RETURNING");
            }
            if (request.status() == DeliveryTaskStatus.FAILED) {
                returnRequests.findByReturnTask_Id(task.getId()).ifPresent(returnRequest -> {
                    returnRequest.setStatus("EXECUTION_FAILED");
                    returnRequest.setUpdatedAt(Instant.now());
                });
            }
            return;
        }

        if (request.status() == DeliveryTaskStatus.OUT_FOR_DELIVERY) {
            order.setStatus(OrderStatus.IN_TRANSIT);
            order.setFulfillmentStatus("IN_TRANSIT");
        }
        if (request.status() == DeliveryTaskStatus.COMPLETED) {
            order.setStatus(OrderStatus.DELIVERED);
            order.setFulfillmentStatus("DELIVERED");
        }
        if (request.status() == DeliveryTaskStatus.FAILED) {
            order.setStatus(OrderStatus.FAILED);
            order.setFulfillmentStatus("FAILED");
        }
        order.setUpdatedAt(Instant.now());
    }

    private void audit(
            DeliveryTask task,
            DeliveryTaskStatus previous,
            UpdateDeliveryTaskRequest request,
            UUID driverId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderId", task.getOrder().getId().toString());
        details.put("taskType", task.getTaskType());
        details.put("from", previous.name());
        details.put("to", request.status().name());
        if (request.notes() != null && !request.notes().isBlank()) {
            details.put("notes", request.notes());
        }
        if (request.failureReason() != null && !request.failureReason().isBlank()) {
            details.put("failureReason", request.failureReason());
        }
        auditLogs.save(AuditLog.builder()
                .tenantId(task.getTenantId())
                .aggregateType("DELIVERY_TASK")
                .aggregateId(task.getId())
                .action("STATUS_CHANGED")
                .actorId(driverId)
                .details(details)
                .build());
    }

    /**
     * Accepts all (or specified) ASSIGNED tasks belonging to the authenticated driver in a single transaction.
     */
    @Transactional
    public List<DriverDeliveryTaskResponse> batchAccept(
            UUID driverId,
            String role,
            BatchDeliveryTaskActionRequest request) {
        requireDriver(role);
        List<DeliveryTask> candidates = filterCandidateTasks(driverId, request);
        List<DriverDeliveryTaskResponse> updated = new ArrayList<>();
        String notes = request != null ? request.notes() : null;
        for (DeliveryTask task : candidates) {
            if (task.getStatus() == DeliveryTaskStatus.ASSIGNED) {
                DeliveryTask saved = update(
                        task.getId(),
                        driverId,
                        role,
                        new UpdateDeliveryTaskRequest(DeliveryTaskStatus.ACCEPTED, null, notes, null));
                updated.add(DriverDeliveryTaskResponse.from(saved));
            }
        }
        return updated;
    }

    /**
     * Confirms physical pickup for all (or specified depot-grouped) tasks of the driver in one swipe,
     * recording package custody acquisition and transitioning orders directly to IN_TRANSIT / OUT_FOR_DELIVERY.
     */
    @Transactional
    public List<DriverDeliveryTaskResponse> batchPickup(
            UUID driverId,
            String role,
            BatchDeliveryTaskActionRequest request) {
        requireDriver(role);
        List<DeliveryTask> candidates = filterCandidateTasks(driverId, request);
        List<DriverDeliveryTaskResponse> updated = new ArrayList<>();
        String notes = request != null ? request.notes() : null;
        for (DeliveryTask task : candidates) {
            if ("DELIVERY".equals(task.getTaskType())
                    && task.getStatus().ordinal() <= DeliveryTaskStatus.PICKED_UP.ordinal()
                    && task.getStatus() != DeliveryTaskStatus.OUT_FOR_DELIVERY) {
                DeliveryTask saved = update(
                        task.getId(),
                        driverId,
                        role,
                        new UpdateDeliveryTaskRequest(DeliveryTaskStatus.OUT_FOR_DELIVERY, null, notes, null));
                updated.add(DriverDeliveryTaskResponse.from(saved));
            } else if ("RETURN".equals(task.getTaskType())
                    && (task.getStatus() == DeliveryTaskStatus.ASSIGNED
                            || task.getStatus() == DeliveryTaskStatus.ACCEPTED)) {
                DeliveryTask saved = update(
                        task.getId(),
                        driverId,
                        role,
                        new UpdateDeliveryTaskRequest(DeliveryTaskStatus.OUT_FOR_DELIVERY, null, notes, null));
                updated.add(DriverDeliveryTaskResponse.from(saved));
            }
        }
        return updated;
    }

    private List<DeliveryTask> filterCandidateTasks(UUID driverId, BatchDeliveryTaskActionRequest request) {
        List<DeliveryTask> activeTasks = tasks.findByDriverIdAndStatusIn(driverId, ACTIVE);
        Set<UUID> taskIdFilter = request != null && request.taskIds() != null && !request.taskIds().isEmpty()
                ? new HashSet<>(request.taskIds())
                : Set.of();
        Set<UUID> orderIdFilter = request != null && request.orderIds() != null && !request.orderIds().isEmpty()
                ? new HashSet<>(request.orderIds())
                : Set.of();
        if (taskIdFilter.isEmpty() && orderIdFilter.isEmpty()) {
            return activeTasks;
        }
        return activeTasks.stream()
                .filter(task -> taskIdFilter.contains(task.getId())
                        || (task.getOrder() != null && orderIdFilter.contains(task.getOrder().getId())))
                .toList();
    }

    private boolean allowed(String taskType, DeliveryTaskStatus from, DeliveryTaskStatus to) {
        if (to == DeliveryTaskStatus.FAILED) {
            return ACTIVE.contains(from) && from != DeliveryTaskStatus.ASSIGNED;
        }
        if ("RETURN".equals(taskType)) {
            return switch (from) {
                case ASSIGNED -> to == DeliveryTaskStatus.ACCEPTED || to == DeliveryTaskStatus.OUT_FOR_DELIVERY;
                case ACCEPTED -> to == DeliveryTaskStatus.OUT_FOR_DELIVERY;
                case OUT_FOR_DELIVERY -> to == DeliveryTaskStatus.COMPLETED;
                default -> false;
            };
        }
        return switch (from) {
            case ASSIGNED -> to == DeliveryTaskStatus.ACCEPTED
                    || to == DeliveryTaskStatus.PICKED_UP
                    || to == DeliveryTaskStatus.OUT_FOR_DELIVERY;
            case ACCEPTED -> to == DeliveryTaskStatus.ARRIVED
                    || to == DeliveryTaskStatus.PICKED_UP
                    || to == DeliveryTaskStatus.OUT_FOR_DELIVERY;
            case ARRIVED -> to == DeliveryTaskStatus.PICKED_UP
                    || to == DeliveryTaskStatus.OUT_FOR_DELIVERY;
            case PICKED_UP -> to == DeliveryTaskStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> to == DeliveryTaskStatus.COMPLETED;
            default -> false;
        };
    }

    private void requireDriver(String role) {
        if (!"DRIVER".equalsIgnoreCase(role)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
