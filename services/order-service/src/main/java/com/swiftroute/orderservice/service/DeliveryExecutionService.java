package com.swiftroute.orderservice.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.event.DeliveryCompletedEvent;
import com.swiftroute.orderservice.dto.request.UpdateDeliveryTaskRequest;
import com.swiftroute.orderservice.dto.response.DriverDeliveryTaskResponse;
import com.swiftroute.orderservice.entity.AuditLog;
import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.DeliveryProofConfig;
import com.swiftroute.orderservice.entity.DeliveryTask;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OutboxEvent;
import com.swiftroute.orderservice.entity.ReturnRequest;
import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.entity.enums.ProofPolicy;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.AuditLogRepository;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.DeliveryProofConfigRepository;
import com.swiftroute.orderservice.repository.DeliveryTaskRepository;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
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
    private final OutboxEventRepository outboxEvents;
    private final DispatchPlanRepository dispatchPlans;
    private final RecipientDeliveryEvents recipientDeliveryEvents;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DeliveryProofConfigRepository deliveryProofConfigs;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OrderStatusHistoryRecorder statusHistory;
    @Value("${recipient-notifications.delivery-started.enabled:false}")
    private boolean deliveryStartedEventEnabled;

    @Transactional
    public DeliveryTask assign(Order order, UUID driverId) {
        return tasks.findByOrder_IdAndTaskType(order.getId(), "DELIVERY")
                .map(task -> {
                    if (task.getStatus() == DeliveryTaskStatus.COMPLETED) {
                        throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
                    }
                    task.setDriverId(driverId);
                    task.setStatus(DeliveryTaskStatus.OUT_FOR_DELIVERY);
                    task.setUpdatedAt(Instant.now());
                    return task;
                })
                .orElseGet(() -> tasks.save(DeliveryTask.builder()
                        .order(order)
                        .tenantId(order.getTenant().getId())
                        .driverId(driverId)
                        .status(DeliveryTaskStatus.OUT_FOR_DELIVERY)
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
                    if (task.getStatus() != DeliveryTaskStatus.CANCELLED
                            && task.getStatus() != DeliveryTaskStatus.FAILED) {
                        throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
                    }
                    task.setDriverId(driverId);
                    task.setStatus(DeliveryTaskStatus.OUT_FOR_DELIVERY);
                    task.setUpdatedAt(Instant.now());
                    return task;
                })
                .orElseGet(() -> tasks.save(DeliveryTask.builder()
                        .order(order)
                        .tenantId(order.getTenant().getId())
                        .driverId(driverId)
                        .taskType("RETURN")
                        .status(DeliveryTaskStatus.OUT_FOR_DELIVERY)
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
        OrderStatus previous = order.getStatus();
        task.setDriverId(driverId);
        task.setStatus(DeliveryTaskStatus.OUT_FOR_DELIVERY);
        task.setUpdatedAt(Instant.now());
        order.setAssignedDriverId(driverId);
        order.setStatus(OrderStatus.IN_TRANSIT);
        order.setUpdatedAt(Instant.now());
        recordStatusChange(order, previous, OrderStatus.IN_TRANSIT, driverId, "OPERATOR", "Kích hoạt giao lại đơn hàng");
        return tasks.save(task);
    }

    @Transactional
    public DeliveryTask resumeDeliveryAfterIncident(Order order, UUID driverId) {
        UUID effectiveDriver = driverId != null ? driverId : order.getAssignedDriverId();
        DeliveryTask task = tasks.findByOrder_IdAndTaskType(order.getId(), "DELIVERY")
                .orElseGet(() -> DeliveryTask.builder()
                        .order(order)
                        .tenantId(order.getTenant().getId())
                        .driverId(effectiveDriver)
                        .taskType("DELIVERY")
                        .build());
        if (task.getStatus() == DeliveryTaskStatus.FAILED) {
            task.setStatus(DeliveryTaskStatus.OUT_FOR_DELIVERY);
            task.setUpdatedAt(Instant.now());
        }
        return tasks.save(task);
    }

    @Transactional(readOnly = true)
    public List<DriverDeliveryTaskResponse> activeForDriver(UUID driverId, String role) {
        requireDriver(role);
        return tasks.findByDriverIdAndStatusIn(driverId, ACTIVE).stream()
                .filter(this::matchesCurrentDispatchPlan)
                .filter(task -> task.getOrder().getStatus() != OrderStatus.CANCELLED)
                .map(task -> "RETURN".equals(task.getTaskType())
                        ? DriverDeliveryTaskResponse.from(task, returnRequests.findByReturnTask_Id(task.getId())
                                .map(ReturnRequest::getReturnLocation).orElse(null))
                        : DriverDeliveryTaskResponse.from(task))
                .toList();
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
        if (!matchesCurrentDispatchPlan(task)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        if (task.getOrder().getAssignedDriverId() != null
                && !driverId.equals(task.getOrder().getAssignedDriverId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (previous == request.status()) {
            if ("RETURN".equals(task.getTaskType()) && previous == DeliveryTaskStatus.COMPLETED) {
                return task;
            }
            Order order = task.getOrder();
            applyOrderState(task, order, driverId, request);
            orders.save(order);
            return task;
        }
        if (!allowed(task.getTaskType(), previous, request.status())) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }
        if (request.status() == DeliveryTaskStatus.FAILED
                && (request.failureReason() == null || request.failureReason().isBlank())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        ProofPolicy successNotePolicy = ProofPolicy.OPTIONAL;
        ProofPolicy successPhotoPolicy = ProofPolicy.REQUIRED;
        if ("DELIVERY".equals(task.getTaskType()) && request.status() == DeliveryTaskStatus.COMPLETED
                && deliveryProofConfigs != null) {
            DeliveryProofConfig proofConfig = deliveryProofConfigs.findByTenantId(task.getTenantId()).orElse(null);
            if (proofConfig != null) {
                if (proofConfig.getSuccessNotePolicy() != null) {
                    successNotePolicy = proofConfig.getSuccessNotePolicy();
                }
                if (proofConfig.getSuccessPhotoPolicy() != null) {
                    successPhotoPolicy = proofConfig.getSuccessPhotoPolicy();
                }
            }
            if (successNotePolicy == ProofPolicy.REQUIRED
                    && (request.notes() == null || request.notes().isBlank())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Vui lòng nhập ghi chú giao hàng theo quy định của doanh nghiệp.");
            }
            if (successPhotoPolicy == ProofPolicy.REQUIRED
                    && (request.proofUrl() == null || request.proofUrl().isBlank())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Vui lòng đính kèm hình ảnh bằng chứng giao hàng (POD) theo quy định của doanh nghiệp.");
            }
        }

        Order order = task.getOrder();
        task.setStatus(request.status());
        task.setUpdatedAt(Instant.now());
        applyOrderState(task, order, driverId, request);

        if (deliveryStartedEventEnabled && "DELIVERY".equals(task.getTaskType())
                && request.status() == DeliveryTaskStatus.OUT_FOR_DELIVERY) {
            publishDeliveryStarted(task, order);
        }

        if ("DELIVERY".equals(task.getTaskType()) && request.status() == DeliveryTaskStatus.COMPLETED) {
            publishDeliveryCompleted(task, order);
        }

        if (request.status() == DeliveryTaskStatus.COMPLETED || request.status() == DeliveryTaskStatus.FAILED) {
            String attemptNotes = request.status() == DeliveryTaskStatus.COMPLETED
                    && "DELIVERY".equals(task.getTaskType())
                    && successNotePolicy == ProofPolicy.DISABLED
                            ? null
                            : request.notes();
            String attemptProofUrl = request.status() == DeliveryTaskStatus.COMPLETED
                    && "DELIVERY".equals(task.getTaskType())
                    && successPhotoPolicy == ProofPolicy.DISABLED
                            ? null
                            : request.proofUrl();
            DeliveryAttempt attempt = DeliveryAttempt.builder()
                    .order(order)
                    .task(task)
                    .tenantId(task.getTenantId())
                    .attemptNumber(attempts.maxAttemptNumber(order.getId()) + 1)
                    .outcome(request.status() == DeliveryTaskStatus.COMPLETED ? "SUCCESS" : "FAILED")
                    .failureReason(request.failureReason())
                    .notes(attemptNotes)
                    .proofUrl(attemptProofUrl)
                    .completedBy(driverId)
                    .build();
            attempts.save(attempt);
            if ("DELIVERY".equals(task.getTaskType())) {
                recipientDeliveryEvents.recordResult(attempt);
            }
        }

        orders.save(order);
        audit(task, previous, request, driverId);

        if (request.status() == DeliveryTaskStatus.COMPLETED || request.status() == DeliveryTaskStatus.FAILED) {
            checkAndCompleteDispatchPlan(task);
        }

        return task;
    }

    private void checkAndCompleteDispatchPlan(DeliveryTask task) {
        UUID planId = task.getDispatchPlanId();
        if (planId == null && task.getOrder() != null) {
            planId = task.getOrder().getPlanId();
        }
        completeDeliveryPlanIfFinished(planId);
    }

    public void completeDeliveryPlanIfFinished(Order order) {
        completeDeliveryPlanIfFinished(order.getPlanId());
    }

    private void completeDeliveryPlanIfFinished(UUID planId) {
        if (planId == null) {
            return;
        }
        dispatchPlans.findById(planId).ifPresent(plan -> {
            if (plan.getPlanStatus() != PlanStatus.DISPATCHED) {
                return;
            }
            List<DeliveryTask> planTasks = tasks.findByDispatchPlanId(plan.getId());
            if (planTasks.isEmpty()) {
                return;
            }
            boolean allTerminal = planTasks.stream()
                    .allMatch(t -> t.getStatus() == DeliveryTaskStatus.COMPLETED
                            || t.getStatus() == DeliveryTaskStatus.FAILED
                            || t.getStatus() == DeliveryTaskStatus.CANCELLED);
            if (allTerminal) {
                plan.complete();
                dispatchPlans.save(plan);
                publishPlanCompleted(plan);
            }
        });
    }

    private void publishPlanCompleted(DispatchPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plan_id", plan.getId().toString());
        payload.put("tenant_id", plan.getTenant().getId().toString());
        payload.put("plan_status", plan.getPlanStatus().name());
        payload.put("driver_id", plan.getDriverId() != null ? plan.getDriverId().toString() : null);
        payload.put("completed_at", Instant.now().toString());

        outboxEvents.save(OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("DispatchPlan")
                .aggregateId(plan.getId())
                .eventType("plan.completed")
                .payload(payload)
                .build());
    }

    private void publishDeliveryStarted(DeliveryTask task, Order order) {
        UUID eventId = UUID.randomUUID();
        outboxEvents.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("DELIVERY_TASK")
                .aggregateId(task.getId())
                .eventType(RecipientDeliveryEvents.STARTED)
                .payload(Map.of(
                        "event_id", eventId.toString(),
                        "tenant_id", task.getTenantId().toString(),
                        "order_id", order.getId().toString(),
                        "delivery_task_id", task.getId().toString(),
                        "started_at", task.getUpdatedAt().toString()))
                .build());
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
                completedAt,
                order.getExternalOrderCode(),
                order.getRecipientName(),
                order.getDeliveryAddress(),
                order.getCodAmount());
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
        OrderStatus previous = order.getStatus();
        if ("RETURN".equals(task.getTaskType())) {
            if (request.status() == DeliveryTaskStatus.OUT_FOR_DELIVERY) {
                order.setStatus(OrderStatus.RETURNING);
            }
            if (request.status() == DeliveryTaskStatus.COMPLETED) {
                ReturnRequest returnRequest = returnRequests.findByReturnTask_Id(task.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.INVALID_ORDER_STATUS));
                returnRequest.setStatus("AWAITING_RECEIPT");
                returnRequest.setUpdatedAt(Instant.now());
                if (order.getStatus() != OrderStatus.RETURNED) {
                    order.setStatus(OrderStatus.RETURNING);
                }
            }
            if (request.status() == DeliveryTaskStatus.FAILED) {
                returnRequests.findByReturnTask_Id(task.getId()).ifPresent(returnRequest -> {
                    returnRequest.setStatus("EXECUTION_FAILED");
                    returnRequest.setUpdatedAt(Instant.now());
                });
            }
            order.setUpdatedAt(Instant.now());
            recordStatusChange(order, previous, order.getStatus(), driverId, "DRIVER",
                    buildTransitionReason(task, request, order.getStatus()));
            return;
        }

        if (order.getAssignedDriverId() == null || !order.getAssignedDriverId().equals(driverId)) {
            order.setAssignedDriverId(driverId);
        }
        if (request.status() == DeliveryTaskStatus.ACCEPTED
                || request.status() == DeliveryTaskStatus.ARRIVED
                || request.status() == DeliveryTaskStatus.PICKED_UP) {
            if (order.getStatus() == OrderStatus.READY_FOR_DISPATCH) {
                order.setStatus(OrderStatus.PLANNED);
            }
        }
        if (request.status() == DeliveryTaskStatus.OUT_FOR_DELIVERY) {
            order.setStatus(OrderStatus.IN_TRANSIT);
        }
        if (request.status() == DeliveryTaskStatus.COMPLETED) {
            order.setStatus(OrderStatus.DELIVERED);
        }
        if (request.status() == DeliveryTaskStatus.FAILED) {
            order.setStatus(OrderStatus.FAILED);
        }
        order.setUpdatedAt(Instant.now());
        recordStatusChange(order, previous, order.getStatus(), driverId, "DRIVER",
                buildTransitionReason(task, request, order.getStatus()));
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

    private static String buildTransitionReason(
            DeliveryTask task,
            UpdateDeliveryTaskRequest request,
            OrderStatus targetStatus) {
        if (targetStatus == OrderStatus.FAILED) {
            if (request.notes() != null && !request.notes().isBlank()) {
                return request.failureReason() + ": " + request.notes().trim();
            }
            return request.failureReason();
        }
        if (request.notes() != null && !request.notes().isBlank()) {
            return request.notes().trim();
        }
        return switch (targetStatus) {
            case IN_TRANSIT -> "Tài xế bắt đầu giao hàng";
            case DELIVERED -> "Tài xế xác nhận giao hàng thành công";
            case RETURNING -> "RETURN".equals(task.getTaskType())
                    ? "Tài xế mang hàng hoàn về kho"
                    : "Cập nhật trạng thái hoàn hàng";
            case PLANNED -> "Tài xế xác nhận tiếp nhận đơn";
            default -> "Cập nhật tiến độ giao hàng (" + request.status().name() + ")";
        };
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

    private boolean matchesCurrentDispatchPlan(DeliveryTask task) {
        if (!"DELIVERY".equals(task.getTaskType())) {
            return true;
        }
        // Reject orphaned tasks whose order was released back to the queue or reassigned.
        UUID assignedDriverId = task.getOrder().getAssignedDriverId();
        if (assignedDriverId == null || !assignedDriverId.equals(task.getDriverId())) {
            return false;
        }
        return task.getDispatchPlanId() == null
                || task.getDispatchPlanId().equals(task.getOrder().getPlanId());
    }

    private boolean allowed(String taskType, DeliveryTaskStatus from, DeliveryTaskStatus to) {
        if (to == DeliveryTaskStatus.FAILED) {
            return ACTIVE.contains(from);
        }
        if ("RETURN".equals(taskType)) {
            return switch (from) {
                case ASSIGNED -> to == DeliveryTaskStatus.ACCEPTED || to == DeliveryTaskStatus.OUT_FOR_DELIVERY
                        || to == DeliveryTaskStatus.COMPLETED;
                case ACCEPTED -> to == DeliveryTaskStatus.OUT_FOR_DELIVERY || to == DeliveryTaskStatus.COMPLETED;
                case ARRIVED -> to == DeliveryTaskStatus.COMPLETED;
                case OUT_FOR_DELIVERY -> to == DeliveryTaskStatus.COMPLETED;
                default -> false;
            };
        }
        return switch (from) {
            case ASSIGNED -> to == DeliveryTaskStatus.ACCEPTED
                    || to == DeliveryTaskStatus.PICKED_UP
                    || to == DeliveryTaskStatus.OUT_FOR_DELIVERY
                    || to == DeliveryTaskStatus.COMPLETED;
            case ACCEPTED -> to == DeliveryTaskStatus.ARRIVED
                    || to == DeliveryTaskStatus.PICKED_UP
                    || to == DeliveryTaskStatus.OUT_FOR_DELIVERY
                    || to == DeliveryTaskStatus.COMPLETED;
            case ARRIVED -> to == DeliveryTaskStatus.PICKED_UP
                    || to == DeliveryTaskStatus.OUT_FOR_DELIVERY
                    || to == DeliveryTaskStatus.COMPLETED;
            case PICKED_UP -> to == DeliveryTaskStatus.OUT_FOR_DELIVERY
                    || to == DeliveryTaskStatus.COMPLETED;
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
