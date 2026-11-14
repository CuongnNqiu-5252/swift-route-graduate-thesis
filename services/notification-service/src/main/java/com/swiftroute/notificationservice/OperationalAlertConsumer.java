package com.swiftroute.notificationservice;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes dispatch, delivery, return, COD, and settlement operational events from RabbitMQ
 * and records in-app / push notifications for drivers and tenant operators (P2.7).
 * Broadcasts tenant-relevant events in real-time over SSE to connected tenant operators.
 */
@Component
@Slf4j
public class OperationalAlertConsumer {

    private static final Set<String> IGNORED_ROUTING_KEYS = Set.of(
            "plan.acknowledged_implicitly",
            "return.requested",
            "return.completed",
            "incident.reported");

    private static final Set<String> DRIVER_ONLY_ROUTING_KEYS = Set.of(
            "plan.dispatched",
            "route.updated",
            "return.approved",
            "cod.handover_settled",
            "cod.handover_settled_with_variance",
            "cod.dispute_resolved",
            "settlement.approved",
            "settlement.paid",
            "driver.profile_reviewed");

    private static final Set<String> TENANT_ADDRESSED_ROUTING_KEYS = Set.of(
            "delivery.incident",
            "incident.resolved",
            "order.delivery_completed.v1",
            "plan.acknowledged",
            "plan.cancelled",
            "assignment.timeout",
            "assignment.escalated",
            "driver.unavailable_recovery",
            "delivery.delay_detected",
            "cod.handover_submitted",
            "return.discrepancy");

    private final JdbcTemplate jdbc;
    private final NotificationSseService sseService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public OperationalAlertConsumer(JdbcTemplate jdbc, NotificationSseService sseService) {
        this.jdbc = jdbc;
        this.sseService = sseService;
    }

    public OperationalAlertConsumer(JdbcTemplate jdbc) {
        this(jdbc, null);
    }

    @RabbitListener(queues = TenantNotificationConfig.OPS_ALERT_QUEUE)
    @Transactional
    public void onOperationalEvent(Message message) {
        try {
            String rawJson = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(rawJson);
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            if (routingKey != null && IGNORED_ROUTING_KEYS.contains(routingKey)) {
                return;
            }

            String corrId = message.getMessageProperties().getCorrelationId();
            if (corrId == null) corrId = message.getMessageProperties().getMessageId();

            UUID eventId = parseUuidOrRandom(corrId != null
                    ? corrId
                    : firstNonBlank(payload, "event_id", "eventId"));
            int inserted = jdbc.update(
                    "INSERT INTO processed_events(event_id, consumer_name) VALUES (?, 'operational-alerts') ON CONFLICT DO NOTHING",
                    eventId);
            if (inserted == 0) {
                return;
            }

            UUID tenantId = parseNullableUuid(firstNonBlank(payload, "tenant_id", "tenantId"));
            if (tenantId == null && payload.has("incident_id") && !payload.path("incident_id").asText("").isBlank()) {
                String incId = payload.path("incident_id").asText("");
                List<UUID> existingTenants = jdbc.query(
                        "SELECT tenant_id FROM notification_deliveries WHERE payload->>'incident_id' = ? LIMIT 1",
                        (rs, row) -> rs.getObject("tenant_id", UUID.class), incId);
                if (!existingTenants.isEmpty()) {
                    tenantId = existingTenants.get(0);
                }
            }

            UUID driverId = parseNullableUuid(
                    firstNonBlank(payload, "driver_id", "driverId", "assigned_driver_id", "assignedDriverId"));
            UUID targetUserId = driverId;
            if (routingKey != null && TENANT_ADDRESSED_ROUTING_KEYS.contains(routingKey) && tenantId != null) {
                targetUserId = tenantId;
            }
            if (targetUserId == null && tenantId != null) {
                targetUserId = tenantId;
            }
            if (targetUserId == null) {
                return;
            }

            String title = buildAlertTitle(routingKey, payload);
            String body = buildAlertBody(routingKey, payload);
            String channel = (routingKey != null && DRIVER_ONLY_ROUTING_KEYS.contains(routingKey))
                    ? "PUSH"
                    : "IN_APP";

            jdbc.update("""
                    INSERT INTO notification_deliveries(
                        delivery_id, tenant_id, user_id, channel, template_code, title, body, payload, status, sent_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'SENT', now())
                    """,
                    eventId,
                    tenantId,
                    targetUserId,
                    channel,
                    routingKey,
                    title,
                    body,
                    rawJson);

            log.info("Recorded operational alert notification [{}] for user/tenant {}", routingKey, targetUserId);

            // Broadcast real-time alert over SSE to connected tenant operator web sessions
            if (sseService != null && tenantId != null && (routingKey == null || !DRIVER_ONLY_ROUTING_KEYS.contains(routingKey))) {
                String orderId = firstNonBlank(payload, "order_id", "orderId");
                String planId = firstNonBlank(payload, "dispatch_plan_id", "plan_id", "planId");
                String incidentId = firstNonBlank(payload, "incident_id", "incidentId");

                Map<String, Object> alertDto = new HashMap<>();
                alertDto.put("id", eventId.toString());
                alertDto.put("incident_id", incidentId);
                alertDto.put("driver_id", driverId != null ? driverId.toString() : "");
                alertDto.put("dispatch_plan_id", planId);
                alertDto.put("order_id", orderId);
                alertDto.put("title", title);
                alertDto.put("body", body);
                alertDto.put("severity", resolveSeverity(routingKey, payload));
                alertDto.put("category", resolveCategory(routingKey));
                alertDto.put("action_url", resolveActionUrl(routingKey, incidentId, orderId, planId));
                alertDto.put("template_code", routingKey);
                alertDto.put("created_at", Instant.now().toString());

                if ("incident.resolved".equals(routingKey)) {
                    sseService.broadcast(tenantId, "incident-resolved", alertDto);
                } else if ("delivery.incident".equals(routingKey)) {
                    sseService.broadcast(tenantId, "dispatch-alert", alertDto);
                } else {
                    sseService.broadcast(tenantId, "operational-alert", alertDto);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process operational alert message: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Unable to record operational notification", ex);
        }
    }

    private String buildAlertTitle(String routingKey, JsonNode payload) {
        if (routingKey == null) return "Cảnh báo vận hành SwiftRoute";
        return switch (routingKey) {
            case "order.delivery_completed.v1" -> {
                String code = shortCode(firstNonBlank(payload, "order_code", "orderCode", "order_id", "orderId"));
                yield code.isEmpty() ? "Đơn hàng đã giao thành công" : "Đã giao thành công đơn " + code;
            }
            case "plan.dispatched" -> "Lộ trình giao hàng mới đã được phân công";
            case "plan.acknowledged" -> "Tài xế đã xác nhận nhận lộ trình";
            case "plan.cancelled" -> "Kế hoạch điều phối đã bị hủy";
            case "assignment.timeout" -> "Cảnh báo: Tài xế chưa xác nhận lộ trình đúng hạn";
            case "assignment.escalated" -> "Cần can thiệp: Lộ trình vượt quá số lần tự động gán lại";
            case "delivery.incident" -> "Cảnh báo sự cố giao hàng trên tuyến";
            case "incident.resolved" -> "Đã cập nhật hướng xử lý sự cố giao hàng";
            case "delivery.delay_detected" -> "Cảnh báo nguy cơ trễ khung giờ giao hàng";
            case "driver.unavailable_recovery" -> "Đã kích hoạt thu hồi lộ trình do tài xế ngắt kết nối";
            case "route.updated" -> "Lộ trình giao hàng của bạn vừa được cập nhật";
            case "return.approved" -> "Yêu cầu hoàn hàng về kho đã được duyệt";
            case "return.discrepancy" -> "Cảnh báo chênh lệch khi nhận hàng hoàn tại kho";
            case "cod.handover_submitted" -> "Có phiếu nộp COD mới chờ kiểm đếm";
            case "cod.handover_settled" -> "Phiếu nộp COD đã được quyết toán đủ";
            case "cod.handover_settled_with_variance" -> "Phiếu nộp COD đã được quyết toán có chênh lệch";
            case "cod.dispute_resolved" -> "Đã xử lý khoản chênh lệch COD";
            case "settlement.approved" -> "Kỳ đối soát thu nhập đã được phê duyệt";
            case "settlement.paid" -> "Thu nhập đối soát đã được thanh toán";
            case "driver.profile_reviewed" -> payload.path("approved").asBoolean(true)
                    ? "Hồ sơ tài xế của bạn đã được phê duyệt"
                    : "Hồ sơ tài xế cần bổ sung / chỉnh sửa";
            default -> "Thông báo vận hành: " + routingKey;
        };
    }

    private String buildAlertBody(String routingKey, JsonNode payload) {
        if ("order.delivery_completed.v1".equals(routingKey)) {
            String recipient = firstNonBlank(payload, "recipient_name", "recipientName");
            String address = firstNonBlank(payload, "delivery_address", "deliveryAddress");
            long cod = Math.round(payload.path("codAmount").asDouble(payload.path("cod_amount").asDouble(0)));
            StringBuilder sb = new StringBuilder("Tài xế đã hoàn tất giao hàng");
            if (!recipient.isBlank()) {
                sb.append(" cho ").append(recipient);
            }
            if (!address.isBlank()) {
                sb.append(" tại ").append(address);
            }
            if (cod > 0) {
                sb.append(" · COD: ").append(formatVnd(cod));
            }
            return sb.append('.').toString();
        }
        if ("plan.dispatched".equals(routingKey)) {
            int count = payload.path("order_count").asInt(0);
            return "Bạn vừa được phân công lộ trình gồm " + count + " điểm giao. Hãy xác nhận trên ứng dụng.";
        }
        if ("plan.acknowledged".equals(routingKey)) {
            int count = payload.path("order_count").asInt(0);
            return "Tài xế đã nhận lộ trình (" + count + " đơn) và bắt đầu di chuyển đến kho lấy hàng.";
        }
        if ("plan.cancelled".equals(routingKey)) {
            int count = payload.path("order_count").asInt(0);
            return "Kế hoạch điều phối (" + count + " đơn) đã bị hủy hoặc từ chối. Các đơn đã quay về hàng đợi điều phối.";
        }
        if ("assignment.timeout".equals(routingKey)) {
            return "Tài xế không phản hồi nhận lộ trình trước thời hạn. Hệ thống đang tự động tìm tài xế thay thế.";
        }
        if ("assignment.escalated".equals(routingKey)) {
            return "Không tìm được tài xế thay thế sau khi hết hạn nhận lộ trình. Vui lòng điều phối thủ công.";
        }
        if ("delivery.incident".equals(routingKey)) {
            String incType = translateIncidentType(payload.path("incident_type").asText("UNKNOWN"));
            String action = translateRecoveryAction(payload.path("recovery_action").asText("MANUAL_REVIEW"));
            String notes = payload.path("notes").asText("");
            return "Sự cố: " + incType
                    + (notes.isBlank() ? "" : " — \"" + notes + "\"")
                    + ". Đề xuất: " + action + ".";
        }
        if ("incident.resolved".equals(routingKey)) {
            String resType = translateResolutionType(payload.path("resolution_type").asText("RESOLVED"));
            String notes = payload.path("notes").asText("");
            String code = shortCode(firstNonBlank(payload, "order_code", "order_id"));
            return (code.isEmpty() ? "Sự cố giao hàng" : "Đơn " + code)
                    + " đã được xử lý: " + resType
                    + (notes.isBlank() ? "." : " (" + notes + ").");
        }
        if ("return.approved".equals(routingKey)) {
            String code = shortCode(firstNonBlank(payload, "order_code", "order_id"));
            String warehouse = payload.path("return_location_name").asText("kho lấy hàng");
            return "Yêu cầu hoàn hàng " + (code.isEmpty() ? "" : "đơn " + code + " ")
                    + "đã được duyệt. Vui lòng bàn giao kiện hàng về " + warehouse + ".";
        }
        if ("return.discrepancy".equals(routingKey)) {
            String type = payload.path("discrepancy_type").asText("SAI_LECH");
            return "Kho nhận hàng hoàn ghi nhận sai lệch (" + type + "). Đã mở hồ sơ sự cố cần kiểm tra.";
        }
        if ("cod.handover_submitted".equals(routingKey)) {
            long declared = Math.round(payload.path("declared_total").asDouble(0));
            int count = payload.path("order_count").asInt(1);
            return "Tài xế vừa lập phiếu nộp " + formatVnd(declared)
                    + " tiền thu hộ COD (" + count + " đơn). Vui lòng kiểm đếm tại quầy.";
        }
        if ("cod.handover_settled".equals(routingKey)) {
            long counted = Math.round(payload.path("counted_total").asDouble(0));
            return "Quầy thu ngân đã quyết toán đủ " + formatVnd(counted) + " cho phiếu nộp COD của bạn.";
        }
        if ("cod.handover_settled_with_variance".equals(routingKey)) {
            long expected = Math.round(payload.path("ledger_total").asDouble(0));
            long counted = Math.round(payload.path("counted_total").asDouble(0));
            return "Phiếu nộp COD đã được quyết toán có chênh lệch (Sổ sách: " + formatVnd(expected)
                    + " · Thực đếm: " + formatVnd(counted) + "). Xem ghi chú để biết kết luận.";
        }
        if ("cod.dispute_resolved".equals(routingKey)) {
            String resolution = translateCodResolutionType(payload.path("resolution_type").asText("RESOLVED"));
            String notes = payload.path("notes").asText("");
            return "Khoản chênh lệch bàn giao COD đã được kết luận: " + resolution
                    + (notes.isBlank() ? "." : " (" + notes + ").");
        }
        if ("settlement.approved".equals(routingKey)) {
            long total = Math.round(payload.path("total_amount").asDouble(0));
            int count = payload.path("earning_count").asInt(0);
            return "Kỳ đối soát gồm " + count + " chuyến giao (" + formatVnd(total) + ") đã được phê duyệt thanh toán.";
        }
        if ("settlement.paid".equals(routingKey)) {
            long total = Math.round(payload.path("total_amount").asDouble(0));
            String ref = payload.path("payment_reference").asText("");
            return "Đã thanh toán " + formatVnd(total) + " thu nhập đối soát"
                    + (ref.isBlank() ? "." : " (Mã GD: " + ref + ").");
        }
        if ("driver.profile_reviewed".equals(routingKey)) {
            boolean approved = payload.path("approved").asBoolean(true);
            String note = payload.path("note").asText("");
            return approved
                    ? "Hồ sơ tài xế và phương tiện đã được duyệt. Bạn có thể bật trạng thái sẵn sàng nhận chuyến!"
                    : "Quản lý yêu cầu bổ sung hồ sơ: " + (note.isBlank() ? "Vui lòng kiểm tra lại thông tin." : note);
        }
        if ("delivery.delay_detected".equals(routingKey)) {
            int delayMin = payload.path("estimated_delay_minutes").asInt(0);
            return "Phát hiện nguy cơ giao trễ khoảng " + delayMin + " phút. Hệ thống đang đánh giá tối ưu lại các điểm còn lại.";
        }
        if ("driver.unavailable_recovery".equals(routingKey)) {
            return "Tài xế mất kết nối hoặc báo ngưng đột xuất. Các đơn chưa lấy đã được đưa về hàng đợi điều phối.";
        }
        if ("route.updated".equals(routingKey)) {
            int count = payload.path("order_count").asInt(0);
            return "Điều phối viên vừa cập nhật thứ tự hoặc thêm đơn vào lộ trình của bạn (hiện có " + count + " điểm dừng).";
        }
        return "Sự kiện vận hành [" + routingKey + "] vừa được ghi nhận trên hệ thống.";
    }

    static String resolveCategory(String routingKey) {
        if (routingKey == null) return "SYSTEM";
        if (routingKey.contains("incident") || "return.discrepancy".equals(routingKey)) return "INCIDENT";
        if (routingKey.startsWith("cod.")) return "COD";
        if (routingKey.startsWith("settlement.")) return "EARNINGS";
        if ("order.delivery_completed.v1".equals(routingKey) || routingKey.startsWith("return.")) return "DELIVERY";
        if (routingKey.startsWith("plan.") || routingKey.startsWith("assignment.")
                || routingKey.startsWith("route.") || "driver.unavailable_recovery".equals(routingKey)
                || "delivery.delay_detected".equals(routingKey)) {
            return "DISPATCH";
        }
        return "WORKSPACE";
    }

    static String resolveSeverity(String routingKey, JsonNode payload) {
        if (payload != null && payload.hasNonNull("severity") && !payload.path("severity").asText("").isBlank()) {
            return payload.path("severity").asText("MEDIUM");
        }
        if (routingKey == null) return "LOW";
        return switch (routingKey) {
            case "assignment.escalated", "driver.unavailable_recovery", "return.discrepancy" -> "HIGH";
            case "assignment.timeout", "delivery.delay_detected", "plan.cancelled", "cod.handover_submitted",
                 "cod.handover_settled_with_variance" -> "MEDIUM";
            default -> "LOW";
        };
    }

    static String resolveActionUrl(String routingKey, String incidentId, String orderId, String planId) {
        if (routingKey == null) return "/operations";
        return switch (routingKey) {
            case "delivery.incident" -> incidentId != null && !incidentId.isBlank()
                    ? "/operations?incident=" + incidentId
                    : "/operations";
            case "incident.resolved", "cod.handover_submitted", "cod.handover_settled_with_variance", "return.discrepancy" -> "/operations";
            case "order.delivery_completed.v1" -> "/orders";
            case "plan.acknowledged", "plan.cancelled", "assignment.timeout", "assignment.escalated",
                 "driver.unavailable_recovery", "delivery.delay_detected" -> "/dispatch";
            case "settlement.approved", "settlement.paid" -> "/settlements";
            default -> "/operations";
        };
    }

    private static String firstNonBlank(JsonNode node, String... keys) {
        if (node == null) return "";
        for (String key : keys) {
            String value = node.path(key).asText("");
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String shortCode(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String trimmed = raw.trim();
        if (trimmed.length() <= 14) return trimmed;
        return "#" + trimmed.substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String formatVnd(long amount) {
        return NumberFormat.getInstance(Locale.of("vi", "VN")).format(amount) + " ₫";
    }

    private static String translateIncidentType(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CUSTOMER_ABSENT", "RECIPIENT_UNAVAILABLE" -> "Khách vắng mặt";
            case "CUSTOMER_UNREACHABLE" -> "Không liên lạc được người nhận";
            case "CUSTOMER_REFUSED", "REFUSED", "REFUSED_BY_RECIPIENT" -> "Khách từ chối nhận hàng";
            case "WRONG_ADDRESS" -> "Sai địa chỉ giao hàng";
            case "DAMAGED", "PACKAGE_DAMAGED", "GOODS_DAMAGED" -> "Kiện hàng hư hỏng";
            case "VEHICLE_BREAKDOWN" -> "Hỏng xe trên tuyến";
            case "WEATHER_DELAY" -> "Thời tiết xấu / ngập đường";
            default -> type;
        };
    }

    private static String translateRecoveryAction(String action) {
        return switch (action.toUpperCase(Locale.ROOT)) {
            case "REDELIVERY" -> "Giao lại";
            case "RETURN_TO_ORIGIN" -> "Hoàn hàng về kho";
            case "CONTINUE_DELIVERY" -> "Tiếp tục giao";
            default -> "Chờ điều phối viên xử lý";
        };
    }

    private static String translateResolutionType(String resType) {
        return switch (resType.toUpperCase(Locale.ROOT)) {
            case "CONTINUE_DELIVERY" -> "Tiếp tục giao đơn hàng";
            case "RETRY_DELIVERY" -> "Lên lịch giao lại";
            case "REASSIGN_DRIVER" -> "Điều chuyển tài xế khác";
            case "REQUEST_RETURN" -> "Tạo yêu cầu hoàn hàng về kho";
            case "RETURN_COMPLETED" -> "Đã hoàn hàng về kho thành công";
            case "ESCALATED" -> "Chuyển cấp quản lý xử lý";
            default -> resType;
        };
    }

    private static String translateCodResolutionType(String resType) {
        return switch (resType.toUpperCase(Locale.ROOT)) {
            case "DRIVER_REIMBURSEMENT", "DRIVER_REIMBURSED", "CASH_RECOVERED" -> "Tài xế bù phần thiếu";
            case "COMPANY_ADJUSTMENT" -> "Doanh nghiệp ghi nhận điều chỉnh";
            case "COUNTING_CORRECTION", "COUNT_CORRECTED" -> "Sửa sai số kiểm đếm";
            case "CUSTOMER_DEBT", "CUSTOMER_SETTLED" -> "Chuyển thành công nợ khách hàng";
            case "WAIVED", "WRITE_OFF" -> "Miễn / đóng chênh lệch";
            case "REFUND" -> "Hoàn trả phần thừa";
            default -> resType;
        };
    }

    private UUID parseUuidOrRandom(String raw) {
        UUID parsed = parseNullableUuid(raw);
        return parsed != null ? parsed : UUID.randomUUID();
    }

    private UUID parseNullableUuid(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
