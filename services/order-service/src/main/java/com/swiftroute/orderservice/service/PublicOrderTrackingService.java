package com.swiftroute.orderservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.orderservice.dto.response.PublicOrderTrackingResponse;
import com.swiftroute.orderservice.dto.response.PublicTrackingMilestoneResponse;
import com.swiftroute.orderservice.entity.DeliveryAttempt;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OrderStatusHistory;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DeliveryAttemptRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OrderStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds PII-masked, customer-safe order tracking responses for unauthenticated recipients.
 */
@Service
@RequiredArgsConstructor
public class PublicOrderTrackingService {

    static final Duration TERMINAL_TTL = Duration.ofDays(60);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Z0-9_-]{6,64}$");

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;

    /**
     * Resolves an order by public tracking token, enforces terminal TTL, masks PII,
     * and excludes internal operator notes, handovers, and return discrepancy records.
     */
    @Transactional(readOnly = true)
    public PublicOrderTrackingResponse getByTrackingToken(String rawToken) {
        String normalizedToken = normalizeToken(rawToken);
        if (normalizedToken == null) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        Order order = orderRepository.findByTrackingToken(normalizedToken)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (isExpiredTerminalOrder(order)) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        List<PublicTrackingMilestoneResponse> milestones = new ArrayList<>();
        addStatusMilestones(order, milestones);
        String latestProofUrl = addDeliveryAttemptMilestones(order, milestones);

        milestones.sort(Comparator
                .comparing(PublicTrackingMilestoneResponse::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PublicTrackingMilestoneResponse::id));

        String carrierName = order.getTenant() != null && isNonBlank(order.getTenant().getBusinessName())
                ? order.getTenant().getBusinessName().trim()
                : "SwiftRoute Express";

        String orderCode = isNonBlank(order.getExternalOrderCode())
                ? order.getExternalOrderCode().trim()
                : "#" + order.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        return new PublicOrderTrackingResponse(
                order.getTrackingToken(),
                orderCode,
                carrierName,
                order.getStatus(),
                progressStep(order.getStatus()),
                maskName(order.getSenderName()),
                maskAddressArea(order.getPickupAddress()),
                maskName(order.getRecipientName()),
                maskPhone(order.getRecipientPhone()),
                maskAddressArea(order.getDeliveryAddress()),
                order.getPackageCategory(),
                trimToNull(order.getPackageDescription()),
                order.getWeightKg(),
                order.getCodAmount(),
                order.getCodStatus(),
                order.getTimeWindowStart(),
                order.getTimeWindowEnd(),
                latestProofUrl,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                List.copyOf(milestones));
    }

    private void addStatusMilestones(Order order, List<PublicTrackingMilestoneResponse> milestones) {
        List<OrderStatusHistory> history = statusHistoryRepository.findAllByOrder_IdOrderByCreatedAtDesc(order.getId());
        if (!history.isEmpty()) {
            for (OrderStatusHistory entry : history) {
                milestones.add(new PublicTrackingMilestoneResponse(
                        "status-" + entry.getId(),
                        entry.getFromStatus() == null ? "ORDER_CREATED" : "ORDER_STATUS_CHANGED",
                        publicStatusTitle(entry.getToStatus()),
                        publicStatusDescription(entry.getToStatus()),
                        entry.getToStatus(),
                        null,
                        entry.getCreatedAt()));
            }
            return;
        }

        milestones.add(new PublicTrackingMilestoneResponse(
                "status-created-" + order.getId(),
                "ORDER_CREATED",
                publicStatusTitle(OrderStatus.READY_FOR_DISPATCH.name()),
                publicStatusDescription(OrderStatus.READY_FOR_DISPATCH.name()),
                OrderStatus.READY_FOR_DISPATCH.name(),
                null,
                order.getCreatedAt()));

        if (order.getStatus() != null && order.getStatus() != OrderStatus.READY_FOR_DISPATCH) {
            milestones.add(new PublicTrackingMilestoneResponse(
                    "status-current-" + order.getId(),
                    "ORDER_STATUS_CHANGED",
                    publicStatusTitle(order.getStatus().name()),
                    publicStatusDescription(order.getStatus().name()),
                    order.getStatus().name(),
                    null,
                    order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt()));
        }
    }

    private String addDeliveryAttemptMilestones(Order order, List<PublicTrackingMilestoneResponse> milestones) {
        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findAllByOrder_IdOrderByCompletedAtDesc(order.getId());
        String latestProofUrl = null;
        for (DeliveryAttempt attempt : attempts) {
            String proofUrl = trimToNull(attempt.getProofUrl());
            if (latestProofUrl == null && proofUrl != null) {
                latestProofUrl = proofUrl;
            }
            boolean success = "SUCCESS".equalsIgnoreCase(attempt.getOutcome());
            int number = attempt.getAttemptNumber() != null ? attempt.getAttemptNumber() : 1;
            String title = (success ? "Giao hàng thành công" : "Giao chưa thành công") + " (Lần " + number + ")";
            String description = success
                    ? "Kiện hàng đã được giao đến địa chỉ nhận"
                    : friendlyFailureReason(attempt.getFailureReason());

            milestones.add(new PublicTrackingMilestoneResponse(
                    "attempt-" + attempt.getId(),
                    success ? "DELIVERY_ATTEMPT_SUCCESS" : "DELIVERY_ATTEMPT_FAILED",
                    title,
                    description,
                    attempt.getOutcome(),
                    proofUrl,
                    attempt.getCompletedAt()));
        }
        return latestProofUrl;
    }

    private static boolean isExpiredTerminalOrder(Order order) {
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.DELIVERED && status != OrderStatus.RETURNED && status != OrderStatus.CANCELLED) {
            return false;
        }
        Instant referenceTime = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
        return referenceTime != null && referenceTime.isBefore(Instant.now().minus(TERMINAL_TTL));
    }

    private static String normalizeToken(String rawToken) {
        if (!isNonBlank(rawToken)) {
            return null;
        }
        String normalized = rawToken.trim().toUpperCase(Locale.ROOT);
        return TOKEN_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    static int progressStep(OrderStatus status) {
        if (status == null) {
            return 1;
        }
        return switch (status) {
            case READY_FOR_DISPATCH -> 1;
            case PLANNED -> 2;
            case IN_TRANSIT, FAILED, RETURNING -> 3;
            case DELIVERED, RETURNED -> 4;
            case CANCELLED -> 0;
        };
    }

    static String maskName(String fullName) {
        if (!isNonBlank(fullName)) {
            return "—";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            String single = parts[0];
            return single.length() <= 1 ? single + "***" : single.charAt(0) + "***";
        }
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(' ').append(parts[i].charAt(0)).append('.');
            }
        }
        return sb.toString();
    }

    static String maskPhone(String phone) {
        if (!isNonBlank(phone)) {
            return "—";
        }
        String compact = phone.replaceAll("\\s+", "");
        if (compact.length() < 7) {
            return "***";
        }
        return compact.substring(0, 3) + "***" + compact.substring(compact.length() - 3);
    }

    static String maskAddressArea(String address) {
        if (!isNonBlank(address)) {
            return "—";
        }
        List<String> segments = Arrays.stream(address.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (segments.size() >= 2) {
            return segments.get(segments.size() - 2) + ", " + segments.get(segments.size() - 1);
        }
        if (segments.size() == 1) {
            String stripped = segments.get(0).replaceFirst("^\\d+[\\w/-]*\\s+", "").trim();
            return stripped.isEmpty() ? "***" : stripped;
        }
        return "—";
    }

    private static String publicStatusTitle(String status) {
        if (status == null) {
            return "Cập nhật đơn hàng";
        }
        return switch (status) {
            case "READY_FOR_DISPATCH" -> "Đã tiếp nhận vận đơn";
            case "PLANNED" -> "Đã lên kế hoạch giao hàng";
            case "IN_TRANSIT" -> "Tài xế đang trên đường giao hàng";
            case "DELIVERED" -> "Giao hàng thành công";
            case "FAILED" -> "Giao hàng chưa thành công";
            case "RETURNING" -> "Đang chuyển hoàn về người gửi";
            case "RETURNED" -> "Đã hoàn hàng về người gửi";
            case "CANCELLED" -> "Đơn hàng đã huỷ";
            default -> "Cập nhật trạng thái đơn hàng";
        };
    }

    private static String publicStatusDescription(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "READY_FOR_DISPATCH" -> "Đơn hàng đã được tạo và đang chờ điều phối";
            case "PLANNED" -> "Đơn hàng đã được xếp vào tuyến giao dự kiến";
            case "IN_TRANSIT" -> "Đơn vị vận chuyển đang giao kiện hàng tới bạn";
            case "DELIVERED" -> "Kiện hàng đã được bàn giao cho người nhận";
            case "FAILED" -> "Chuyến giao gần nhất gặp gián đoạn, đơn vị vận chuyển sẽ xử lý tiếp";
            case "RETURNING" -> "Kiện hàng đang trên đường hoàn trả về kho gửi";
            case "RETURNED" -> "Kiện hàng đã hoàn trả về người gửi";
            case "CANCELLED" -> "Đơn hàng đã được huỷ trước khi giao hoàn tất";
            default -> null;
        };
    }

    private static String friendlyFailureReason(String rawReason) {
        if (!isNonBlank(rawReason)) {
            return "Chưa thể hoàn tất giao hàng trong lượt này";
        }
        return switch (rawReason.trim().toUpperCase(Locale.ROOT)) {
            case "CUSTOMER_UNREACHABLE", "RECIPIENT_UNAVAILABLE" -> "Chưa liên lạc được với người nhận";
            case "WRONG_ADDRESS", "ADDRESS_NOT_FOUND" -> "Địa chỉ giao hàng cần xác nhận lại";
            case "CUSTOMER_REFUSED", "RECIPIENT_REFUSED" -> "Người nhận từ chối nhận hàng";
            case "RESCHEDULE_REQUESTED", "CUSTOMER_RESCHEDULED" -> "Người nhận hẹn giao lại vào thời gian khác";
            case "DAMAGED_GOODS", "PACKAGE_DAMAGED" -> "Kiện hàng gặp sự cố hư hỏng trong quá trình vận chuyển";
            case "TRAFFIC_CONGESTION", "WEATHER_DELAY" -> "Chuyến giao bị chậm do giao thông hoặc thời tiết";
            default -> "Chưa thể hoàn tất giao hàng trong lượt này";
        };
    }

    private static String trimToNull(String value) {
        return isNonBlank(value) ? value.trim() : null;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
