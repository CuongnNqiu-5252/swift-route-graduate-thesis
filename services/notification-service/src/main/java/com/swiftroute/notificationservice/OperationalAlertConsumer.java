package com.swiftroute.notificationservice;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes dispatch and exception operational events from RabbitMQ and records
 * in-app / push notifications for drivers and tenant operators (P2.7).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationalAlertConsumer {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = TenantNotificationConfig.OPS_ALERT_QUEUE)
    @Transactional
    public void onOperationalEvent(Message message) {
        try {
            String rawJson = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(rawJson);
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            String corrId = message.getMessageProperties().getCorrelationId();

            UUID eventId = parseUuidOrRandom(corrId != null ? corrId : payload.path("event_id").asText());
            int inserted = jdbc.update(
                    "INSERT INTO processed_events(event_id, consumer_name) VALUES (?, 'operational-alerts') ON CONFLICT DO NOTHING",
                    eventId);
            if (inserted == 0) {
                return;
            }

            UUID tenantId = parseNullableUuid(payload.path("tenant_id").asText(payload.path("tenantId").asText()));
            UUID targetUserId = parseNullableUuid(payload.path("driver_id").asText(payload.path("driverId").asText()));
            if (targetUserId == null && tenantId != null) {
                targetUserId = tenantId; // Fallback logical recipient for tenant-wide operator alerts
            }
            if (targetUserId == null) {
                return;
            }

            String title = buildAlertTitle(routingKey);
            String body = buildAlertBody(routingKey, payload);
            String channel = "plan.dispatched".equals(routingKey) || "route.updated".equals(routingKey)
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
        } catch (Exception ex) {
            log.error("Failed to process operational alert message: {}", ex.getMessage(), ex);
        }
    }

    private String buildAlertTitle(String routingKey) {
        if (routingKey == null) return "Cảnh báo vận hành SwiftRoute";
        return switch (routingKey) {
            case "plan.dispatched" -> "Lộ trình giao hàng mới đã được phân công";
            case "plan.cancelled" -> "Kế hoạch điều phối đã bị hủy";
            case "assignment.timeout" -> "Cảnh báo: Tài xế chưa xác nhận lộ trình đúng hạn";
            case "assignment.escalated" -> "Cần can thiệp: Lộ trình vượt quá số lần tự động gán lại";
            case "delivery.incident" -> "Cảnh báo sự cố giao hàng trên tuyến";
            case "delivery.delay_detected" -> "Cảnh báo nguy cơ trễ khung giờ giao hàng";
            case "driver.unavailable_recovery" -> "Đã kích hoạt thu hồi lộ trình do tài xế ngắt kết nối";
            case "route.updated" -> "Lộ trình giao hàng của bạn vừa được cập nhật";
            default -> "Thông báo vận hành: " + routingKey;
        };
    }

    private String buildAlertBody(String routingKey, JsonNode payload) {
        if ("plan.dispatched".equals(routingKey)) {
            int count = payload.path("order_count").asInt(0);
            return "Bạn vừa được phân công lộ trình gồm " + count + " điểm giao. Hãy xác nhận trên ứng dụng.";
        }
        if ("delivery.incident".equals(routingKey)) {
            String incType = payload.path("incident_type").asText("UNKNOWN");
            String action = payload.path("recovery_action").asText("MANUAL_REVIEW");
            return "Sự cố [" + incType + "] đã được ghi nhận. Hướng xử lý tự động: " + action + ".";
        }
        if ("delivery.delay_detected".equals(routingKey)) {
            int delayMin = payload.path("estimated_delay_minutes").asInt(0);
            return "Phát hiện nguy cơ giao trễ khoảng " + delayMin + " phút. Hệ thống đang đánh giá tối ưu lại các điểm còn lại.";
        }
        return "Sự kiện vận hành [" + routingKey + "] vừa được ghi nhận trên hệ thống.";
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

