package com.swiftroute.notificationservice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes tenant workflow and account events from RabbitMQ and stores
 * in-app notifications, broadcasting updates via SSE to connected operators.
 */
@Component
@Slf4j
public class TenantNotificationConsumer {
    private final JdbcTemplate jdbc;
    private final NotificationSseService sseService;
    private final EmailNotificationSender emailSender;
    private final ObjectMapper json = new ObjectMapper();

    public TenantNotificationConsumer(JdbcTemplate jdbc, @Autowired(required = false) NotificationSseService sseService,
            EmailNotificationSender emailSender) {
        this.jdbc = jdbc;
        this.sseService = sseService;
        this.emailSender = emailSender;
    }

    @RabbitListener(queues = TenantNotificationConfig.QUEUE)
    @Transactional
    public void receive(Message message) throws Exception {
        var p = json.readTree(message.getBody());
        String type = p.path("eventType").asText(p.path("event_type").asText());
        if (!Set.of("tenant.driver_invited.v1", "user.email_verification.v1", "user.password_reset_requested.v1", "tenant.created.v1",
                "tenant.verification_submitted.v1", "tenant.verification_changes_requested.v1", "tenant.approved.v1",
                "tenant.rejected.v1", "tenant.suspended.v1").contains(type)) {
            return;
        }

        UUID eventId = UUID.fromString(p.path("eventId").asText(p.path("event_id").asText()));
        if (jdbc.update(
                "INSERT INTO processed_events(event_id, consumer_name) VALUES (?, 'tenant-notifications') ON CONFLICT DO NOTHING",
                eventId) == 0) {
            return;
        }

        String user = p.path("userId").asText(p.path("ownerUserId").asText());
        UUID tenant = p.hasNonNull("tenantId") ? UUID.fromString(p.get("tenantId").asText()) : null;

        String title = switch (type) {
            case "user.email_verification.v1" -> "Xác minh tài khoản SwiftRoute";
            case "user.password_reset_requested.v1" -> "Đặt lại mật khẩu SwiftRoute";
            case "tenant.driver_invited.v1" -> "Lời mời tham gia đội ngũ giao hàng";
            case "tenant.created.v1" -> "Doanh nghiệp mới đã được tạo";
            case "tenant.approved.v1" -> "Hồ sơ doanh nghiệp đã được phê duyệt";
            case "tenant.rejected.v1" -> "Hồ sơ doanh nghiệp đã bị từ chối";
            case "tenant.suspended.v1" -> "Doanh nghiệp đã bị tạm ngưng hoạt động";
            default -> "Cập nhật workspace SwiftRoute";
        };

        String body = switch (type) {
            case "user.email_verification.v1" ->
                "Tài khoản đăng ký: " + p.path("email").asText()
                + "\nLiên kết xác thực: " + p.path("verificationUrl").asText();
            case "tenant.driver_invited.v1" -> {
                String businessName = p.path("businessName").asText("Doanh nghiệp SwiftRoute");
                yield businessName + " đã gửi lời mời tham gia đội ngũ giao hàng."
                    + "\nLiên kết chấp nhận: " + p.path("verificationUrl").asText();
            }
            case "user.password_reset_requested.v1" ->
                "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản " + p.path("email").asText()
                + ".\nLiên kết có hiệu lực trong 15 phút: " + p.path("verificationUrl").asText()
                + "\nNếu bạn không yêu cầu, hãy bỏ qua email này.";
            default -> "Trạng thái: " + type + (p.hasNonNull("reason") ? " - Lý do: " + p.path("reason").asText() : "");
        };

        log.info("Recorded in-app tenant notification [{}]: {} for user/tenant {} | Content: {}",
                type, title, user != null && !user.isBlank() ? user : tenant, body);

        UUID recipientId = (user != null && !user.isBlank()) ? UUID.fromString(user) : tenant;
        if (recipientId == null) {
            return;
        }

        jdbc.update("""
                INSERT INTO notification_deliveries(
                    delivery_id, tenant_id, user_id, channel, template_code, title, body, status, sent_at
                ) VALUES (?, ?, ?, 'IN_APP', ?, ?, ?, 'SENT', now())
                """,
                eventId, tenant, recipientId, type, title, body);

        if (type.equals("user.email_verification.v1")
                || type.equals("user.password_reset_requested.v1")
                || type.equals("tenant.driver_invited.v1")) {
            emailSender.send(p.path("email").asText(), title, body);
        }

        // Broadcast over SSE to connected tenant operator sessions
        if (sseService != null && tenant != null) {
            Map<String, Object> alertDto = new HashMap<>();
            alertDto.put("id", eventId.toString());
            alertDto.put("title", title);
            alertDto.put("body", body);
            alertDto.put("severity", "LOW");
            alertDto.put("template_code", type);
            alertDto.put("created_at", Instant.now().toString());
            sseService.broadcast(tenant, "operational-alert", alertDto);
        }
    }
}
